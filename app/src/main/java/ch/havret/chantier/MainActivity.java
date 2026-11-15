package ch.havret.chantier;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Havret Chantier — application Android.
 * L'interface est la page HTML embarquée (assets/index.html) ; cette activité
 * lui donne l'appareil photo, la galerie, le partage et l'appel à l'API Claude.
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 42;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0f3b4c"));
        getWindow().setNavigationBarColor(Color.parseColor("#0f3b4c"));

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                openPicker(params.isCaptureEnabled(), params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                return true;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    /** Ouvre l'appareil photo (bouton « Prendre une photo ») ou la galerie (bouton « Galerie »). */
    private void openPicker(boolean camera, boolean multiple) {
        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("image/*");
        if (multiple) gallery.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        Intent shoot = null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "chantier_" + System.currentTimeMillis() + ".jpg");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Havret Chantier");
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (cameraUri != null) {
                shoot = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                shoot.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                shoot.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } catch (Exception e) {
            cameraUri = null;
        }

        Intent chooser;
        if (camera && shoot != null) {
            chooser = shoot;
        } else {
            chooser = Intent.createChooser(gallery, "Choisir des photos");
            if (shoot != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{shoot});
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        List<Uri> out = new ArrayList<>();
        if (resultCode == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) out.add(data.getClipData().getItemAt(i).getUri());
            } else if (data != null && data.getData() != null) {
                out.add(data.getData());
            } else if (cameraUri != null) {
                out.add(cameraUri);
            }
        }
        if (cameraUri != null && !out.contains(cameraUri)) {
            try { getContentResolver().delete(cameraUri, null, null); } catch (Exception ignored) { }
        }
        fileCallback.onReceiveValue(out.isEmpty() ? null : out.toArray(new Uri[0]));
        fileCallback = null;
        cameraUri = null;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__back?window.__back():false)", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    /** Fonctions appelées depuis la page : window.Android.share(), window.Android.analyse(). */
    private class Bridge {

        @JavascriptInterface
        public void share(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(i, "Envoyer")));
        }

        @JavascriptInterface
        public void copy(String text) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Havret", text));
        }

        /** Envoie les photos (base64 JPEG) et la consigne à l'API Claude, puis rappelle window.__aiDone(id, texte, erreur). */
        @JavascriptInterface
        public void analyse(final String id, final String key, final String model, final String prompt, final String imagesJson) {
            new Thread(() -> {
                String text = "", err = "";
                try {
                    JSONArray content = new JSONArray();
                    JSONArray imgs = new JSONArray(imagesJson);
                    for (int i = 0; i < imgs.length(); i++) {
                        JSONObject src = new JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", imgs.getString(i));
                        content.put(new JSONObject().put("type", "image").put("source", src));
                    }
                    content.put(new JSONObject().put("type", "text").put("text", prompt));
                    JSONObject body = new JSONObject()
                            .put("model", model)
                            .put("max_tokens", 2500)
                            .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", content)));

                    HttpURLConnection c = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(120000);
                    c.setDoOutput(true);
                    c.setRequestProperty("content-type", "application/json");
                    c.setRequestProperty("x-api-key", key);
                    c.setRequestProperty("anthropic-version", "2023-06-01");
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = c.getResponseCode();
                    String resp = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
                    if (code >= 400) {
                        String msg = resp;
                        try { msg = new JSONObject(resp).getJSONObject("error").getString("message"); } catch (Exception ignored) { }
                        err = (code == 401 ? "clé API refusée" : "code " + code) + " – " + msg;
                    } else {
                        JSONArray parts = new JSONObject(resp).getJSONArray("content");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject p = parts.getJSONObject(i);
                            if ("text".equals(p.optString("type"))) sb.append(p.optString("text"));
                        }
                        text = sb.toString();
                    }
                } catch (Exception e) {
                    err = "pas de connexion ou réponse illisible (" + e.getClass().getSimpleName() + ")";
                }
                final String js = "window.__aiDone(" + JSONObject.quote(id) + "," + JSONObject.quote(text) + "," + JSONObject.quote(err) + ")";
                web.post(() -> web.evaluateJavascript(js, null));
            }).start();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}
