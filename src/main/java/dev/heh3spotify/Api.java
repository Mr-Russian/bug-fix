package dev.heh3spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Your Spotify account through the official Web API. You paste one key (the Client ID of a free Spotify developer app) and
 * log in once in the browser. After that the saved refresh token logs in silently every time.
 * Login uses PKCE, so there is no client secret to keep.
 */
final class Api {
    private Api() {}

    static String accountsBase = "https://accounts.spotify.com";
    static String apiBase = "https://api.spotify.com/v1";
    static final String SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing";
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    static volatile String status = "Not connected";
    static volatile boolean connected;

    private static String refreshToken = "", accessToken = "";
    private static long expiresAt;
    private static boolean started;
    private static volatile String artUrl = "";
    private static final SecureRandom RNG = new SecureRandom();
    private static final ScheduledExecutorService EX = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heh3-spotify-api");
        t.setDaemon(true);
        return t;
    });

    record Resp(int code, String body) {}

    // ---------------- start-up ----------------
    static synchronized void ensure() {
        if (started) return;
        started = true;
        loadRefresh();
        if (!refreshToken.isEmpty()) { connected = true; status = "Connected"; }
        EX.scheduleWithFixedDelay(Api::poll, 500, 1500, TimeUnit.MILLISECONDS);
    }

    static Path authFile() { return FabricLoader.getInstance().getConfigDir().resolve("heh3-spotify-auth.json"); }

    private static void loadRefresh() {
        try {
            Path f = authFile();
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                refreshToken = str(o, "refreshToken");
            }
        } catch (Exception ignored) {}
    }

    private static void saveRefresh() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("refreshToken", refreshToken);
            Files.writeString(authFile(), o.toString());
        } catch (IOException ignored) {}
    }

    static synchronized void disconnect() {
        refreshToken = ""; accessToken = ""; expiresAt = 0; connected = false; artUrl = "";
        try { Files.deleteIfExists(authFile()); } catch (IOException ignored) {}
        status = "Not connected";
        Media.setIdle("Not connected");
        Media.artPx = null;
        say("Disconnected from Spotify. Your key is still saved; press Connect to log in again.");
    }

    // ---------------- http ----------------
    static Resp http(String method, String url, String body, String contentType, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Heh3-Spotify-HUD/1.0");
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                byte[] b = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                if (contentType != null) c.setRequestProperty("Content-Type", contentType);
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream o = c.getOutputStream()) { o.write(b); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Resp(code, text);
        } finally {
            c.disconnect();
        }
    }

    static byte[] getBytes(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "Heh3-Spotify-HUD/1.0");
            try (InputStream in = c.getInputStream()) { return in.readAllBytes(); }
        } finally {
            c.disconnect();
        }
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    // ---------------- tokens ----------------
    /** Makes sure there is a valid access token (refreshing it silently when needed). */
    static synchronized boolean token() {
        if (!accessToken.isEmpty() && System.currentTimeMillis() < expiresAt - 30000) return true;
        if (refreshToken.isEmpty() || SpotifyMod.cfg.clientId.isEmpty()) return false;
        try {
            String form = "grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(SpotifyMod.cfg.clientId);
            Resp r = http("POST", accountsBase + "/api/token", form, "application/x-www-form-urlencoded", null);
            if (r.code() != 200) {
                if (r.body().contains("invalid_grant") || r.body().contains("invalid_client")) {
                    refreshToken = ""; accessToken = ""; connected = false;
                    try { Files.deleteIfExists(authFile()); } catch (IOException ignored) {}
                    status = "Login expired - press Connect again";
                } else status = "Token error " + r.code();
                return false;
            }
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            accessToken = str(o, "access_token");
            expiresAt = System.currentTimeMillis() + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600) * 1000L;
            String nr = str(o, "refresh_token");
            if (!nr.isEmpty() && !nr.equals(refreshToken)) { refreshToken = nr; saveRefresh(); }
            status = "Connected";
            return !accessToken.isEmpty();
        } catch (Exception e) {
            status = "Can't reach Spotify";
            return false;
        }
    }

    // ---------------- polling ----------------
    static void poll() {
        try {
            if (!SpotifyMod.api() || !connected) return;
            if (!token()) { Media.setIdle(status); return; }
            Resp r = http("GET", apiBase + "/me/player?additional_types=episode", null, null, accessToken);
            if (r.code() == 401) {
                accessToken = "";
                if (!token()) { Media.setIdle(status); return; }
                r = http("GET", apiBase + "/me/player?additional_types=episode", null, null, accessToken);
            }
            if (r.code() == 204 || (r.code() == 200 && r.body().isBlank())) { Media.setIdle("No active Spotify device. Open Spotify and play something."); return; }
            if (r.code() == 429) { Media.setIdle("Spotify is rate limiting; slowing down"); return; }
            if (r.code() != 200) { Media.setIdle("Spotify error " + r.code()); return; }
            parsePlayer(r.body());
        } catch (Exception e) {
            Media.setIdle("Can't reach Spotify");
        }
    }

    /** Reads GET /me/player. Package-visible so it can be tested with sample responses. */
    static void parsePlayer(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        JsonObject item = obj(o, "item");
        if (item == null) { Media.setIdle("Nothing playing (an ad or unknown track)"); return; }
        String title = str(item, "name");
        StringBuilder artists = new StringBuilder();
        JsonArray arr = item.has("artists") && item.get("artists").isJsonArray() ? item.getAsJsonArray("artists") : new JsonArray();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            if (artists.length() > 0) artists.append(", ");
            artists.append(str(e.getAsJsonObject(), "name"));
        }
        JsonObject album = obj(item, "album"), show = obj(item, "show");
        String albumName = album != null ? str(album, "name") : show != null ? str(show, "name") : "";
        if (artists.length() == 0 && show != null) artists.append(str(show, "publisher"));
        JsonArray imgs = album != null && album.has("images") && album.get("images").isJsonArray() ? album.getAsJsonArray("images")
            : item.has("images") && item.get("images").isJsonArray() ? item.getAsJsonArray("images") : new JsonArray();
        JsonObject dev = obj(o, "device");
        int vol = dev != null && dev.has("volume_percent") && !dev.get("volume_percent").isJsonNull() ? dev.get("volume_percent").getAsInt() : -1;
        String rep = str(o, "repeat_state");
        Media.applyApi(title, artists.toString(), albumName,
            o.has("is_playing") && o.get("is_playing").getAsBoolean() ? 3 : 4,
            o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0,
            item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0,
            o.has("shuffle_state") && o.get("shuffle_state").getAsBoolean(),
            rep.equals("track") ? 1 : rep.equals("context") ? 2 : 0,
            vol, dev != null ? str(dev, "name") : "");
        String url = pickImage(imgs);
        if (!url.equals(artUrl)) {
            artUrl = url;
            if (url.isEmpty()) Media.artPx = null;
            else EX.submit(() -> loadArt(url));
        }
    }

    /** The image closest to 300 px (but at least 160 px when there is one). */
    static String pickImage(JsonArray imgs) {
        String best = "";
        int bestScore = Integer.MAX_VALUE;
        for (JsonElement e : imgs) {
            if (!e.isJsonObject()) continue;
            JsonObject im = e.getAsJsonObject();
            String u = str(im, "url");
            if (u.isEmpty()) continue;
            int w = im.has("width") && !im.get("width").isJsonNull() ? im.get("width").getAsInt() : 300;
            int score = Math.abs(w - 300) + (w < 160 ? 1000 : 0);
            if (score < bestScore) { bestScore = score; best = u; }
        }
        return best;
    }

    private static void loadArt(String url) {
        try {
            int[] px = decode(getBytes(url), Media.ART_N);
            if (px != null && url.equals(artUrl)) Media.artPx = px;
        } catch (Throwable ignored) { /* no AWT or bad image: the HUD keeps its gradient placeholder */ }
    }

    /** Image bytes (JPEG or PNG) to an n x n grid of RGB values, averaging the pixels that fall in each cell. */
    static int[] decode(byte[] data, int n) throws IOException {
        BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) return null;
        int w = img.getWidth(), h = img.getHeight();
        int[] out = new int[n * n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int x0 = i * w / n, x1 = Math.max(x0 + 1, (i + 1) * w / n), y0 = j * h / n, y1 = Math.max(y0 + 1, (j + 1) * h / n);
                long r = 0, g = 0, b = 0;
                int cnt = 0;
                for (int y = y0; y < y1 && y < h; y++) {
                    for (int x = x0; x < x1 && x < w; x++) {
                        int p = img.getRGB(x, y);
                        r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255;
                        cnt++;
                    }
                }
                cnt = Math.max(1, cnt);
                out[j * n + i] = ((int) (r / cnt) << 16) | ((int) (g / cnt) << 8) | (int) (b / cnt);
            }
        }
        return out;
    }

    // ---------------- commands ----------------
    static void play(boolean on) { call("PUT", on ? "/me/player/play" : "/me/player/pause"); }
    static void next() { call("POST", "/me/player/next"); }
    static void prev() { call("POST", "/me/player/previous"); }
    static void seek(long ms) { call("PUT", "/me/player/seek?position_ms=" + Math.max(0, ms)); }
    static void volume(int pct) { call("PUT", "/me/player/volume?volume_percent=" + Math.max(0, Math.min(100, pct))); }
    static void shuffle(boolean on) { call("PUT", "/me/player/shuffle?state=" + on); }
    static void repeat(String mode) { call("PUT", "/me/player/repeat?state=" + mode); }

    private static void call(String method, String path) {
        EX.submit(() -> {
            try {
                if (!token()) { Media.notice(status); return; }
                Resp r = http(method, apiBase + path, "", null, accessToken);
                if (r.code() == 401) {
                    accessToken = "";
                    if (!token()) { Media.notice(status); return; }
                    r = http(method, apiBase + path, "", null, accessToken);
                }
                if (r.code() == 403) Media.notice("Controlling playback needs Spotify Premium");
                else if (r.code() == 404) Media.notice("No active Spotify device. Open Spotify and play something.");
                else if (r.code() == 429) Media.notice("Spotify is rate limiting, try again in a moment");
                else if (r.code() >= 300) Media.notice("Spotify error " + r.code());
                EX.schedule(Api::poll, 350, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Media.notice("Can't reach Spotify");
            }
        });
    }

    // ---------------- login (once) ----------------
    static String redirectUri() { return "http://127.0.0.1:" + SpotifyMod.cfg.port + "/callback"; }

    static void connect() {
        if (SpotifyMod.cfg.clientId.isEmpty()) { say("Paste your key first: /spotify key <Client ID>   (see /spotify setup)"); return; }
        ensure();
        Thread t = new Thread(Api::loginFlow, "heh3-spotify-login");
        t.setDaemon(true);
        t.start();
    }

    static String random(int n, String alphabet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        return sb.toString();
    }

    static String challenge(String verifier) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
    }

    static String authUrl(String verifier, String state) throws Exception {
        return accountsBase + "/authorize?response_type=code&client_id=" + enc(SpotifyMod.cfg.clientId) + "&scope=" + enc(SCOPES)
            + "&redirect_uri=" + enc(redirectUri()) + "&state=" + state + "&code_challenge_method=S256&code_challenge=" + challenge(verifier);
    }

    private static void loginFlow() {
        var cfg = SpotifyMod.cfg;
        String verifier = random(64, ALPHABET), state = random(16, ALPHABET.substring(0, 62));
        try (ServerSocket ss = new ServerSocket(cfg.port, 4, InetAddress.getByName("127.0.0.1"))) {
            ss.setSoTimeout(180000);
            String url = authUrl(verifier, state);
            status = "Waiting for you to log in in your browser...";
            openBrowser(url);
            say("Opening your browser to log in to Spotify (one time). If nothing opens, copy this link: " + url);
            String code = null, err = null;
            for (int i = 0; i < 8 && code == null && err == null; i++) {
                try (Socket s = ss.accept()) {
                    s.setSoTimeout(5000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String line = in.readLine();
                    String h;
                    while ((h = in.readLine()) != null && !h.isEmpty()) { /* skip headers */ }
                    boolean cb = line != null && line.startsWith("GET /callback");
                    java.util.Map<String, String> q = cb ? query(line) : java.util.Map.of();
                    if (cb) {
                        if (q.containsKey("error")) err = q.get("error");
                        else if (!state.equals(q.get("state"))) err = "state mismatch";
                        else code = q.get("code");
                    }
                    respond(s, cb ? (code != null ? "Connected to Spotify. You can close this tab and go back to Minecraft."
                        : "Login did not finish (" + err + "). Go back to Minecraft and try again.") : "");
                }
            }
            if (code == null) { status = "Login failed: " + (err == null ? "no answer" : err); say(status); return; }
            String form = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(redirectUri())
                + "&client_id=" + enc(cfg.clientId) + "&code_verifier=" + enc(verifier);
            Resp r = http("POST", accountsBase + "/api/token", form, "application/x-www-form-urlencoded", null);
            if (r.code() != 200) { status = "Login failed (" + r.code() + "). Check the key and the redirect URI."; say(status); return; }
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            synchronized (Api.class) {
                accessToken = str(o, "access_token");
                refreshToken = str(o, "refresh_token");
                expiresAt = System.currentTimeMillis() + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600) * 1000L;
                saveRefresh();
                connected = !refreshToken.isEmpty();
            }
            cfg.source = "api";
            SpotifyMod.save();
            status = "Connected";
            say("Connected to Spotify. You will not need to log in again.");
            poll();
        } catch (java.net.BindException e) {
            status = "Port " + cfg.port + " is busy. Close whatever uses it or change the port in the config.";
            say(status);
        } catch (java.net.SocketTimeoutException e) {
            status = "Login timed out. Press Connect to try again.";
            say(status);
        } catch (Exception e) {
            status = "Login failed: " + e.getMessage();
            say(status);
        }
    }

    static java.util.Map<String, String> query(String requestLine) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return m;
        int qi = parts[1].indexOf('?');
        if (qi < 0) return m;
        for (String kv : parts[1].substring(qi + 1).split("&")) {
            int eq = kv.indexOf('=');
            if (eq < 0) continue;
            m.put(URLDecoder.decode(kv.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void respond(Socket s, String message) throws IOException {
        String html = "<!doctype html><meta charset=utf-8><title>Heh3 Spotify</title><body style=\"font-family:sans-serif;background:#0b0f1c;color:#fff;"
            + "display:flex;align-items:center;justify-content:center;height:100vh\"><h2>" + message.replace("<", "&lt;") + "</h2>";
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
        OutputStream o = s.getOutputStream();
        o.write(head.getBytes(StandardCharsets.US_ASCII));
        o.write(b);
        o.flush();
    }

    static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
                : os.contains("mac") ? new ProcessBuilder("open", url) : new ProcessBuilder("xdg-open", url);
            pb.start();
        } catch (Exception ignored) { /* the link was also printed in chat */ }
    }

    static void setupHelp() {
        say("One-time setup (about 2 minutes):");
        say("1. Open https://developer.spotify.com/dashboard , log in and press Create app (any name). Tick Web API.");
        say("2. Add this Redirect URI exactly, then Save:  " + redirectUri());
        say("3. Copy the app's Client ID, then type  /spotify key <Client ID>");
        say("4. Type  /spotify connect  and log in once in the browser. Done: it never asks again.");
    }

    // ---------------- helpers ----------------
    static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> SpotifyMod.say(s));
    }

    static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }
}
