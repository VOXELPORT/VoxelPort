package com.voxelport.mod.logic;

import java.net.URI;
import java.net.URISyntaxException;

public final class RelayUrlResolver {
    // The relay's WebSocket endpoint lives at the "/ws" path. The bare host root
    // serves an HTML status page and rejects the WS upgrade (handshake fails with
    // close 1006), so the path must be included or the host can never register.
    public static final String DEFAULT_RELAY_URL = "wss://relay.voxelport.in/ws";
    // The same relay reached directly instead of through Cloudflare, which
    // routes some Indian ISPs via Singapore (~130 ms extra). Tried first when
    // the default relay is in use; DEFAULT_RELAY_URL is the fallback.
    public static final String DIRECT_RELAY_URL = "wss://direct.voxelport.in:26499/ws";

    // User-configured override (set by VoxelPortConfig on load / settings save)
    private static volatile String override;

    private RelayUrlResolver() {}

    public static String get() {
        if (override != null) return override;
        return DEFAULT_RELAY_URL;
    }

    /**
     * Ordered URLs to try for a configured relay: the direct endpoint then the
     * default for the official relay, or just the URL itself for a custom one.
     */
    public static java.util.List<String> candidates(String relayWs) {
        if (relayWs != null && DEFAULT_RELAY_URL.equalsIgnoreCase(relayWs.trim())) {
            return java.util.List.of(DIRECT_RELAY_URL, DEFAULT_RELAY_URL);
        }
        return java.util.List.of(relayWs);
    }

    public static boolean isDefaultRelayUrl(String url) {
        return DEFAULT_RELAY_URL.equalsIgnoreCase(normalizeOfficialRelayUrl(url));
    }

    public static boolean isOfficialHost(String host) {
        if (host == null) return false;
        return host.equalsIgnoreCase("relay.voxelport.in")
                || host.equalsIgnoreCase("play.voxelport.in")
                || host.equalsIgnoreCase("voxelport.in")
                || host.equalsIgnoreCase("www.voxelport.in");
    }

    public static boolean hasInvalidOfficialPort(String url) {
        try {
            URI uri = new URI(url.trim());
            return isOfficialHost(uri.getHost()) && uri.getPort() != -1 && uri.getPort() != 443;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String normalizeOfficialRelayUrl(String url) {
        if (url == null || url.isBlank()) return "";
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed);
            if (!"wss".equalsIgnoreCase(uri.getScheme())) return trimmed;
            String host = uri.getHost();
            if (!isOfficialHost(host)) return trimmed;
            if (uri.getPort() != -1 && uri.getPort() != 443) return trimmed;
            String path = uri.getPath();
            if (path != null && !path.isBlank() && !path.equals("/")) return trimmed;
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null) return trimmed;
            // Every official host maps to the single canonical relay URL.
            return DEFAULT_RELAY_URL;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return trimmed;
        }
    }

    /** Called by VoxelPortConfig when the user saves a custom relay URL. */
    public static void setOverride(String url) {
        String normalized = normalizeOfficialRelayUrl(url);
        if (normalized == null || normalized.isBlank()) {
            override = null;
            return;
        }
        // The official relay URL clears the override (it is the default); any other
        // (custom / self-hosted) relay is kept as an override.
        override = DEFAULT_RELAY_URL.equalsIgnoreCase(normalized) ? null : normalized;
    }
}
