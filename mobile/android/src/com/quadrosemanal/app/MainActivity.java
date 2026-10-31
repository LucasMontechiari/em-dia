package com.quadrosemanal.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Tela única: mostra o quadro (assets/index.html) e expõe window.AndroidBridge para o JavaScript. */
public class MainActivity extends Activity {
    private static final int REQ_NOTIF = 1;
    private static final int REQ_FILE = 2;
    private static final String PERM = "android.permission.POST_NOTIFICATIONS";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Scheduler.createChannel(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                // Links externos abrem no navegador do celular.
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Escolher backup"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        setContentView(web);
        if (saved != null) web.restoreState(saved);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onAppResume && onAppResume()", null);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (res == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        refreshNotifUI();
    }

    private void refreshNotifUI() {
        web.post(() -> web.evaluateJavascript("window.syncNotifUI && syncNotifUI()", null));
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("app", Context.MODE_PRIVATE);
    }

    private void openNotifSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try { startActivity(i); } catch (Exception ignored) { }
    }

    /** Funções que o JavaScript chama via window.AndroidBridge. */
    class Bridge {
        @JavascriptInterface
        public String notifStatus() {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED) {
                    return nm.areNotificationsEnabled() ? "granted" : "denied";
                }
                boolean asked = prefs().getBoolean("asked", false);
                return asked && !shouldShowRequestPermissionRationale(PERM) ? "denied" : "default";
            }
            return nm.areNotificationsEnabled() ? "granted" : "denied";
        }

        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(() -> {
                String st = notifStatus();
                if (Build.VERSION.SDK_INT >= 33 && "default".equals(st)) {
                    prefs().edit().putBoolean("asked", true).apply();
                    requestPermissions(new String[]{PERM}, REQ_NOTIF);
                } else {
                    openNotifSettings();
                }
            });
        }

        @JavascriptInterface
        public void sync(String json) {
            Scheduler.sync(getApplicationContext(), json);
        }

        @JavascriptInterface
        public void share(String text, String name) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, name);
                i.putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(i, "Salvar backup")); } catch (Exception ignored) { }
            });
        }
    }
}
