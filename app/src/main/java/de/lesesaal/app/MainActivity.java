package de.lesesaal.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String START = "https://claude.ai/artifact/VbXGUpKKowM9LNwWgq1nKC";
    private static final int FILE_REQ = 41;
    private WebView web;
    private Button loginBtn;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#000000"));
        w.setNavigationBarColor(Color.parseColor("#000000"));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#000000"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#000000"));
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        loginBtn = new Button(this);
        loginBtn.setText("Login-Link einfügen");
        loginBtn.setAllCaps(false);
        loginBtn.setTextColor(Color.parseColor("#000000"));
        loginBtn.setBackgroundColor(Color.parseColor("#FFFFFF"));
        loginBtn.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = (int) (32 * getResources().getDisplayMetrics().density);
        root.addView(loginBtn, lp);
        loginBtn.setOnClickListener(v -> pasteLoginLink());
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "LesesaalAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleUrl(req.getUrl());
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                updateLoginButton(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                String[] types = p.getAcceptTypes();
                String type = "*/*";
                if (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty() && types[0].contains("/")) type = types[0];
                i.setType(type);
                if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(Intent.createChooser(i, "Datei wählen"), FILE_REQ); }
                catch (ActivityNotFoundException e) { fileCb = null; return false; }
                return true;
            }
            @Override
            public boolean onCreateWindow(WebView view, boolean dialog, boolean user, Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        Uri u = r.getUrl();
                        if (!handleUrl(u)) web.loadUrl(u.toString());
                        tmp.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(tmp);
                msg.sendToTarget();
                return true;
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                String name = URLUtil.guessFileName(url, cd, mime);
                web.evaluateJavascript("(async()=>{try{const b=await (await fetch('" + url + "')).blob();const r=new FileReader();r.onload=()=>LesesaalAndroid.save('" + name.replace("'", "") + "',b.type||'" + mime + "',r.result.split(',')[1]);r.readAsDataURL(b)}catch(e){LesesaalAndroid.fail()}})()", null);
            } else {
                openExternal(Uri.parse(url));
            }
        });

        Uri data = getIntent() != null ? getIntent().getData() : null;
        if (state != null) web.restoreState(state);
        else web.loadUrl(data != null && isInternal(data) ? data.toString() : START);
    }

    private boolean isInternal(Uri u) {
        String h = u.getHost();
        if (h == null) return true;
        return h.equals("claude.ai") || h.endsWith(".claude.ai") || h.endsWith("anthropic.com")
            || h.endsWith("claudeusercontent.com") || h.endsWith("claude.com");
    }

    private boolean handleUrl(Uri u) {
        String scheme = u.getScheme();
        String host = u.getHost() == null ? "" : u.getHost();
        if (host.endsWith("accounts.google.com") || host.endsWith("appleid.apple.com")) {
            runOnUiThread(() -> new android.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Bitte mit E-Mail anmelden")
                .setMessage("Google und Apple erlauben die Anmeldung in App-Fenstern nicht.\n\n1. Gib auf der Login-Seite deine E-Mail-Adresse ein.\n2. Halte in der E-Mail von Anthropic den Login-Link gedrückt → „Link kopieren“.\n3. Tippe hier unten auf „Login-Link einfügen“.")
                .setPositiveButton("Verstanden", null).show());
            return true;
        }
        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isInternal(u)) return false;
            openExternal(u);
            return true;
        }
        openExternal(u);
        return true;
    }

    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (Exception e) { Toast.makeText(this, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show(); }
    }

    private void updateLoginButton(String url) {
        boolean login = url != null && (url.contains("claude.ai/login") || url.contains("claude.ai/magic-link") || url.contains("claude.ai/onboarding"));
        loginBtn.setVisibility(login ? View.VISIBLE : View.GONE);
    }

    private void pasteLoginLink() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cb != null ? cb.getPrimaryClip() : null;
        String text = clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null ? clip.getItemAt(0).getText().toString().trim() : "";
        if (text.startsWith("https://claude.ai/")) { web.loadUrl(text); }
        else Toast.makeText(this, "Kopiere zuerst den Login-Link aus der E-Mail von Anthropic", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri d = intent.getData();
        if (d != null && isInternal(d)) web.loadUrl(d.toString());
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != FILE_REQ || fileCb == null) return;
        Uri[] result = null;
        if (res == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) result = new Uri[]{data.getData()};
        }
        fileCb.onReceiveValue(result);
        fileCb = null;
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    private class Bridge {
        @JavascriptInterface
        public void save(String name, String mime, String b64) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                } else {
                    File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Gespeichert in Downloads: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) { fail(); }
        }
        @JavascriptInterface
        public void fail() {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Download fehlgeschlagen", Toast.LENGTH_SHORT).show());
        }
    }
}
