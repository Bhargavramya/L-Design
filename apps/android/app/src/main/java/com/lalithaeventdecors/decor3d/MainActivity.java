package com.lalithaeventdecors.decor3d;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

/**
 * Decor 3D for Android.
 * Opens the live Decor 3D site, so every site update reaches the app automatically.
 * Designs stay on the phone and keep working offline after the first open.
 */
public class MainActivity extends Activity {

    static final String HOME = "https://bhargavramya.github.io/L-Design/";
    static final String HOST = "bhargavramya.github.io";
    static final String OFFLINE = "file:///android_asset/offline.html";
    static final int PICK_FILE = 1001;

    WebView web;
    ValueCallback<Uri[]> pendingPick;

    // Saves files the page "downloads" (pictures, PDFs, SketchUp files, videos) into Downloads/Decor 3D.
    static final String HOOK_JS =
        "(function(){if(window.__d3dHook)return;window.__d3dHook=1;" +
        "var rv=URL.revokeObjectURL.bind(URL);URL.revokeObjectURL=function(u){setTimeout(function(){rv(u);},60000);};" +
        "function send(href,name){fetch(href).then(function(r){return r.blob();}).then(function(b){" +
        "var fr=new FileReader();fr.onload=function(){var s=String(fr.result);" +
        "Decor3DAndroid.save(s.slice(s.indexOf(',')+1),name||'decor3d-file',b.type||'application/octet-stream');};" +
        "fr.readAsDataURL(b);}).catch(function(e){Decor3DAndroid.fail(String(e));});}" +
        "function handle(a){if(a&&a.hasAttribute&&a.hasAttribute('download')&&/^(blob:|data:)/.test(a.href)){send(a.href,a.getAttribute('download'));return true;}return false;}" +
        "var oc=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){if(handle(this))return;return oc.call(this);};" +
        "document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a[download]');if(a&&handle(a))e.preventDefault();},true);" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xff1e1f24);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " Decor3DApp/android");

        web.addJavascriptInterface(new Bridge(), "Decor3DAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (scheme.equals("https") && HOST.equals(u.getHost())) return false;
                if (scheme.equals("file")) return false;
                openOutside(u);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith("https://")) view.evaluateJavascript(HOOK_JS, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame() && !req.getUrl().toString().startsWith("file:")) view.loadUrl(OFFLINE);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingPick != null) pendingPick.onReceiveValue(null);
                pendingPick = callback;
                boolean imagesOnly = true;
                String[] types = params.getAcceptTypes();
                if (types == null || types.length == 0) imagesOnly = false;
                else for (String t : types) if (t == null || !t.startsWith("image/")) { imagesOnly = false; break; }
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(imagesOnly ? "image/*" : "*/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "Choose file"), PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingPick = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                String js = "fetch(" + jsString(url) + ").then(function(r){return r.blob();}).then(function(b){var fr=new FileReader();" +
                    "fr.onload=function(){var s=String(fr.result);Decor3DAndroid.save(s.slice(s.indexOf(',')+1)," + jsString(name) + ",b.type||'application/octet-stream');};fr.readAsDataURL(b);})";
                web.evaluateJavascript(js, null);
            } else {
                openOutside(Uri.parse(url));
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(HOME);
    }

    static String jsString(String v) {
        return "'" + v.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
    }

    void openOutside(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || pendingPick == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        pendingPick.onReceiveValue(result);
        pendingPick = null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // first let the page close an open panel or menu
        web.evaluateJavascript("(window.__appBack&&window.__appBack())?'1':'0'", v -> {
            if ("\"1\"".equals(v)) return;
            if (web.canGoBack()) web.goBack();
            else finish();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    class Bridge {
        @JavascriptInterface
        public void save(String base64, String name, String mime) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Decor 3D");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("could not create file");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("could not write file");
                    os.write(bytes);
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved in Downloads › Decor 3D: " + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                fail(String.valueOf(e.getMessage()));
            }
        }

        @JavascriptInterface
        public void fail(String why) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not save the file. Please try again.", Toast.LENGTH_LONG).show());
        }
    }
}
