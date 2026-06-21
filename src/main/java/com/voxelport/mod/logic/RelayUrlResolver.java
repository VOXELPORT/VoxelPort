package com.voxelport.mod.logic;

public final class RelayUrlResolver {
    private static final String DEFAULT_RELAY_URL = "wss://voxelport.in";

    // User-configured override (set by VoxelPortConfig on load / settings save)
    private static volatile String override;

    private RelayUrlResolver() {}

    public static String get() {
        if (override != null) return override;
        return DEFAULT_RELAY_URL;
    }

    /** Called by VoxelPortConfig when the user saves a custom relay URL. */
    public static void setOverride(String url) {
        override = (url == null || url.isBlank()) ? null : url.trim();
    }
}
