package com.voxelport.mod.logic;

import java.net.URI;
import java.net.URISyntaxException;

public final class RelayUrlResolver {
    // The relay's WebSocket endpoint lives at the "/ws" path. The bare host root
    // serves an HTML status page and rejects the WS upgrade (handshake fails with
    // close 1006), so the path must be included or the host can never register.
    public static final String DEFAULT_RELAY_URL = "wss://relay.voxelport.in/ws";

    // User-configured override (set by VoxelPortConfig on load / settings save)
    private static volatile String override;

    private RelayUrlResolver() {}

    public static String get() {
        if (override != null) return override;
        return DEFAULT_RELAY_URL;
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
