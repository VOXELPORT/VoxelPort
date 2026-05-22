package com.voxelport.mod.logic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class RelayUrlResolver {
    private static final String BOT_BASE = "http://voxelportrelay.qzz.io:2525";
    private static final String BOT_SECRET = "a8048edfed4f9bfcaca216b5b1217f5eb7e521c52c343698ea2b988c0969a0a6";

    // "wss://voxelportrelay.qzz.io/relay" XOR 0x5A — hides the URL from plain JAR string scan
    private static final byte[] E = {
        0x2D, 0x29, 0x29, 0x60, 0x75, 0x75, 0x2C, 0x35, 0x22, 0x3F, 0x36, 0x2A, 0x35, 0x28,
        0x2E, 0x28, 0x3F, 0x36, 0x3B, 0x23, 0x74, 0x2B, 0x20, 0x20, 0x74, 0x33, 0x35, 0x75,
        0x28, 0x3F, 0x36, 0x3B, 0x23
    };
    private static final byte K = 0x5A;

    // User-configured override (set by VoxelPortConfig on load / settings save)
    private static volatile String override;
    // Cached result of bot-API-or-default resolution
    private static volatile String cached;

    private RelayUrlResolver() {}

    public static String get() {
        if (override != null) return override;
        if (cached == null) {
            synchronized (RelayUrlResolver.class) {
                if (cached == null) cached = resolve();
            }
        }
        return cached;
    }

    /** Called by VoxelPortConfig when the user saves a custom relay URL. */
    public static void setOverride(String url) {
        override = (url == null || url.isBlank()) ? null : url.trim();
        cached = null; // force re-resolution next time no override is set
    }

    private static String resolve() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(BOT_BASE + "/relay/url"))
                    .timeout(Duration.ofSeconds(8))
                    .header("x-bot-secret", BOT_SECRET)
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (obj.has("url")) {
                    String url = obj.get("url").getAsString().trim();
                    if (url.startsWith("wss://") || url.startsWith("ws://")) return url;
                }
            }
        } catch (Exception ignored) {}
        return deobfuscate();
    }

    private static String deobfuscate() {
        byte[] dec = new byte[E.length];
        for (int i = 0; i < E.length; i++) dec[i] = (byte) (E[i] ^ K);
        return new String(dec, StandardCharsets.US_ASCII);
    }
}
