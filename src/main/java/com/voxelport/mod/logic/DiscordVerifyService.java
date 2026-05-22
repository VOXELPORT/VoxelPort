package com.voxelport.mod.logic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

public final class DiscordVerifyService {
    private static final String BOT_URL = "http://voxelportrelay.qzz.io:2525";
    private static final String BOT_SECRET = "a8048edfed4f9bfcaca216b5b1217f5eb7e521c52c343698ea2b988c0969a0a6";
    // 12 hours — short enough to limit stale auth, long enough for daily players
    private static final Duration AUTH_VALID_FOR = Duration.ofHours(12);
    private static final String AUTH_FILE = "discord_auth.properties";
    private static final String MACHINE_ID_FILE = "machine.id";

    private final Path dataFolder;
    private final Logger logger;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public DiscordVerifyService(Path dataFolder, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder);
        this.logger = Objects.requireNonNull(logger);
    }

    // --- Public API ---

    public DiscordProfile readCachedProfile() {
        Path file = dataFolder.resolve(AUTH_FILE);
        if (!Files.isRegularFile(file)) return null;
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
            String username = props.getProperty("username");
            String userId   = props.getProperty("userid", "");
            String avatar   = props.getProperty("avatarhash", "");
            String loginDate = props.getProperty("dateoflogin");
            String storedFp = props.getProperty("fingerprint", "");

            if (username == null || loginDate == null || storedFp.isBlank()) return null;

            // Machine-binding check: auth file copied from another machine is rejected
            String expectedFp = computeFingerprint(userId, getMachineId());
            if (!expectedFp.equals(storedFp)) {
                logger.warn("VoxelPort: Discord auth fingerprint mismatch — forcing re-verification.");
                return null;
            }

            DiscordProfile profile = new DiscordProfile(username, userId, avatar, Instant.parse(loginDate));
            return profile.isValid() ? profile : null;
        } catch (Exception e) {
            logger.warn("Failed to read VoxelPort Discord auth cache: {}", e.getMessage());
            return null;
        }
    }

    public void startVerification(String username) throws Exception {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Discord username must not be empty.");
        }
        String machineId = getMachineId();
        String body = "{\"username\":" + toJsonString(username.trim())
                    + ",\"machine_id\":" + toJsonString(machineId) + "}";
        postToBot("verify/start", body);
    }

    public DiscordProfile confirmVerification(String code) throws Exception {
        if (code == null || code.trim().length() != 6) {
            throw new IllegalArgumentException("Code must be exactly 6 digits.");
        }
        String machineId = getMachineId();
        String body = "{\"code\":" + toJsonString(code.trim())
                    + ",\"machine_id\":" + toJsonString(machineId) + "}";
        String responseBody = postToBot("verify/confirm", body);

        JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
        String userId = obj.has("id") ? obj.get("id").getAsString() : "";
        String username = obj.has("globalName") && !obj.get("globalName").isJsonNull()
                ? obj.get("globalName").getAsString() : null;
        if (username == null || username.isBlank()) {
            username = obj.has("username") ? obj.get("username").getAsString() : "unknown";
        }
        String avatarHash = obj.has("avatar") && !obj.get("avatar").isJsonNull()
                ? obj.get("avatar").getAsString() : "";

        DiscordProfile profile = new DiscordProfile(username, userId, avatarHash, Instant.now());
        saveProfile(profile);
        return profile;
    }

    // Called when starting a session to confirm the stored identity is still active
    public boolean validateWithBot(String userId) {
        try {
            String body = "{\"discord_id\":" + toJsonString(userId)
                        + ",\"machine_id\":" + toJsonString(getMachineId()) + "}";
            postToBot("session/validate", body);
            return true;
        } catch (Exception e) {
            // If bot is unreachable, allow (offline tolerance). If bot says 4xx, profile is invalid.
            String msg = e.getMessage();
            if (msg != null && (msg.contains("401") || msg.contains("403") || msg.contains("banned"))) {
                logger.warn("VoxelPort: Bot rejected session validation: {}", msg);
                return false;
            }
            return true; // bot unreachable → don't block
        }
    }

    // --- Private helpers ---

    private String getMachineId() throws IOException {
        Path idFile = dataFolder.resolve(MACHINE_ID_FILE);
        if (Files.isRegularFile(idFile)) {
            String id = Files.readString(idFile, StandardCharsets.UTF_8).trim();
            if (id.matches("[0-9a-f\\-]{36}")) return id;
        }
        String newId = UUID.randomUUID().toString();
        Files.createDirectories(dataFolder);
        setSecureDirectoryPermissions(dataFolder);
        Files.writeString(idFile, newId, StandardCharsets.UTF_8);
        setSecureFilePermissions(idFile);
        return newId;
    }

    private static String computeFingerprint(String userId, String machineId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((userId + "|" + machineId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String postToBot(String endpoint, String jsonBody) throws Exception {
        String url = BOT_URL.endsWith("/") ? BOT_URL + endpoint : BOT_URL + "/" + endpoint;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("x-bot-secret", BOT_SECRET)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 401) {
            throw new IOException("Bot rejected the request — contact VoxelPort support.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String err = tryExtractError(response.body());
            throw new IOException(err != null ? err : "Bot returned error " + response.statusCode());
        }
        return response.body();
    }

    private static String tryExtractError(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            return obj.has("error") ? obj.get("error").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveProfile(DiscordProfile profile) throws IOException {
        Files.createDirectories(dataFolder);
        setSecureDirectoryPermissions(dataFolder);
        Properties props = new Properties();
        props.setProperty("dateoflogin", profile.dateOfLogin().toString());
        props.setProperty("username", profile.username());
        props.setProperty("userid", profile.userId());
        props.setProperty("avatarhash", profile.avatarHash());
        props.setProperty("fingerprint", computeFingerprint(profile.userId(), getMachineId()));
        Path file = dataFolder.resolve(AUTH_FILE);
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "VoxelPort Discord verification cache");
        }
        setSecureFilePermissions(file);
    }

    private static void setSecureDirectoryPermissions(Path path) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
            }
        } catch (Exception ignored) {}
    }

    private static void setSecureFilePermissions(Path path) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (Exception ignored) {}
    }

    private static String toJsonString(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\"").toString();
    }

    public record DiscordProfile(String username, String userId, String avatarHash, Instant dateOfLogin) {
        public boolean isExpired() {
            return dateOfLogin.plus(AUTH_VALID_FOR).isBefore(Instant.now());
        }
        public boolean isValid() {
            return userId != null && !userId.isBlank() && !isExpired();
        }
    }
}
