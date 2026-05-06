package org.localm.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ServerStore {
    private final Path dataDir;
    private final Path serversDb;
    private final Properties servers = new Properties();

    public ServerStore() throws IOException {
        dataDir = Paths.get(System.getenv("APPDATA") == null ? System.getProperty("user.home") : System.getenv("APPDATA"), "VoxelPort");
        serversDb = dataDir.resolve("servers.properties");
        Files.createDirectories(dataDir);
        load();
    }

    public void load() throws IOException {
        if (Files.exists(serversDb)) {
            try (InputStream in = Files.newInputStream(serversDb)) {
                servers.load(in);
            }
        }
    }

    public void save() throws IOException {
        try (OutputStream out = Files.newOutputStream(serversDb)) {
            servers.store(out, "VoxelPort servers");
        }
    }

    public Path getDataDir() {
        return dataDir;
    }

    public String getProperty(String key, String defaultValue) {
        return servers.getProperty(key, defaultValue);
    }

    public String getProperty(String key) {
        return servers.getProperty(key);
    }

    public void setProperty(String key, String value) {
        servers.setProperty(key, value);
    }

    public void remove(String key) {
        servers.remove(key);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getProperty(key, String.valueOf(defaultValue)));
    }

    public Set<String> stringPropertyNames() {
        return servers.stringPropertyNames();
    }

    public boolean containsKey(String key) {
        return servers.containsKey(key);
    }

    public Path getServerDir(String name) {
        String dir = servers.getProperty(name + ".dir");
        if (dir == null) throw new IllegalStateException("Server folder missing");
        return Paths.get(dir);
    }

    public void cleanupOrphans() {
        Set<String> validNames = new HashSet<>();
        for (String key : servers.stringPropertyNames()) {
            if (key.endsWith(".dir")) {
                validNames.add(key.substring(0, key.length() - 4));
            }
        }

        List<String> toRemove = new ArrayList<>();
        for (String key : servers.stringPropertyNames()) {
            int dot = key.lastIndexOf('.');
            if (dot > 0) {
                String name = key.substring(0, dot);
                if (!validNames.contains(name)) toRemove.add(key);
            }
        }

        if (!toRemove.isEmpty()) {
            toRemove.forEach(servers::remove);
            try { save(); } catch (IOException ignored) {}
        }
    }
}
