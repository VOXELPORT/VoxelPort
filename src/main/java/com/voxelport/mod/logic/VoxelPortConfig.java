package com.voxelport.mod.logic;

import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;

public final class VoxelPortConfig {
    private static final String SETTINGS_FILE = "settings.properties";

    private final Path dataFolder;
    private final Logger logger;

    private volatile String relayUrl = "";

    public VoxelPortConfig(Path dataFolder, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder);
        this.logger = Objects.requireNonNull(logger);
    }

    public void load() {
        Path file = dataFolder.resolve(SETTINGS_FILE);
        if (!Files.isRegularFile(file)) return;
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
            relayUrl = props.getProperty("relay_url", "").trim();
            RelayUrlResolver.setOverride(relayUrl.isBlank() ? null : relayUrl);
            logger.info("VoxelPort config loaded. Custom relay: {}", relayUrl.isBlank() ? "(default)" : relayUrl);
        } catch (IOException e) {
            logger.warn("Failed to load VoxelPort settings: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFolder);
            Properties props = new Properties();
            props.setProperty("relay_url", relayUrl);
            Path file = dataFolder.resolve(SETTINGS_FILE);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "VoxelPort Settings — edit in-game via the Settings screen");
            }
        } catch (IOException e) {
            logger.warn("Failed to save VoxelPort settings: {}", e.getMessage());
        }
    }

    public String getRelayUrl() {
        return relayUrl.isBlank() ? null : relayUrl.trim();
    }

    public void setRelayUrl(String url) {
        this.relayUrl = (url == null) ? "" : url.trim();
        RelayUrlResolver.setOverride(this.relayUrl.isBlank() ? null : this.relayUrl);
    }
}
