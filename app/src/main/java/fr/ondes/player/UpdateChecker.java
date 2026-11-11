package fr.ondes.player;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS-only release feed, checked on a background thread. No silent APK installation. */
public final class UpdateChecker {
    public static final class Release {
        public final int versionCode;
        public final String versionName, notes, apkUrl;
        Release(int code, String name, String notes, String apk) {
            versionCode = code; versionName = name; this.notes = notes; apkUrl = apk;
        }
    }
    private UpdateChecker() {}

    public static boolean https(String url) {
        try {
            URL parsed = new URL(url);
            return "https".equalsIgnoreCase(parsed.getProtocol())
                    && parsed.getHost() != null && !parsed.getHost().isEmpty()
                    && parsed.getUserInfo() == null;
        } catch (Exception e) { return false; }
    }

    public static Release fetch(String feedUrl) throws Exception {
        if (!https(feedUrl)) throw new IllegalArgumentException("L'adresse du manifeste doit être en HTTPS.");
        // GitHub's raw CDN may cache update.json briefly after a Release.
        // A fresh query parameter prevents serving a previously published version.
        URL source = new URL(feedUrl);
        String requestUrl = feedUrl;
        if ("raw.githubusercontent.com".equalsIgnoreCase(source.getHost())) {
            requestUrl += (feedUrl.contains("?") ? "&" : "?")
                    + "onde_version_check=" + System.currentTimeMillis();
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
        connection.setUseCaches(false);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Réponse HTTP " + connection.getResponseCode());
            // Limit untrusted feed size to 64 KiB.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = input.read(buffer)) >= 0) {
                    bytes.write(buffer, 0, n);
                    if (bytes.size() > 65536) throw new IllegalStateException("Manifeste trop volumineux");
                }
            }
            JSONObject json = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            int code = json.optInt("versionCode", 0);
            String apk = json.optString("apkUrl", "").trim();
            if (code < 1 || !https(apk)) throw new IllegalStateException("Version ou lien de téléchargement invalide");
            return new Release(code, json.optString("versionName", String.valueOf(code)),
                    json.optString("notes", "Corrections et améliorations."), apk);
        } finally { connection.disconnect(); }
    }
}
