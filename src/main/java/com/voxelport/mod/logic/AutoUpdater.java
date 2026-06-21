package com.voxelport.mod.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AutoUpdater {
    private static final URI LATEST_RELEASE = URI.create(
            "https://api.github.com/repos/VOXELPORT/VoxelPort/releases/latest");

    private AutoUpdater() {}

    public static void checkAsync(String currentVersion, Logger logger, Consumer<String> notifier) {
        CompletableFuture.runAsync(() -> {
            try {
                Update update = findLatestJar(currentVersion);
                if (update == null) return;
                Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
                Files.createDirectories(modsDir);
                Path destination = modsDir.resolve(fileNameFromUrl(update.jarUrl()));
                download(update.jarUrl(), update.sha256Url(), destination, logger);
                notifier.accept("VoxelPort update " + update.version() + " downloaded to mods. Restart Minecraft to use it.");
            } catch (Exception e) {
                logger.warn("VoxelPort auto-update check failed: {}", e.getMessage());
            }
        });
    }

    private static Update findLatestJar(String currentVersion) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VoxelPort-Fabric-Mod")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub releases returned HTTP " + response.statusCode());
        }

        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        String latestVersion = release.has("tag_name") && !release.get("tag_name").isJsonNull()
                ? release.get("tag_name").getAsString() : "latest";

        if (!normalizeVersion(latestVersion).isBlank()
                && normalizeVersion(latestVersion).equalsIgnoreCase(normalizeVersion(currentVersion))) {
            return null;
        }

        String jarUrl = null;
        String sha256Url = null;
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets != null) {
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = asset.get("name").getAsString();
                String url = asset.get("browser_download_url").getAsString();
                if (name.endsWith(".jar")) jarUrl = url;
                else if (name.endsWith(".jar.sha256")) sha256Url = url;
            }
        }

        if (jarUrl == null) throw new IOException("No jar asset found in latest release.");
        if (sha256Url == null) throw new IOException("No checksum file (.jar.sha256) found in release — update rejected.");

        return new Update(latestVersion, jarUrl, sha256Url);
    }

    private static void download(String jarUrl, String sha256Url, Path destination, Logger logger) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(jarUrl))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "VoxelPort-Fabric-Mod")
                .GET()
                .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(destination);
            throw new IOException("Jar download returned HTTP " + response.statusCode());
        }
        verifyChecksum(destination, sha256Url, client, logger);
    }

    private static void verifyChecksum(Path file, String sha256Url, HttpClient client, Logger logger) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(sha256Url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "VoxelPort-Fabric-Mod")
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            Files.deleteIfExists(file);
            throw new IOException("Checksum download returned HTTP " + resp.statusCode());
        }
        String expectedHash = resp.body().trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(file);
        byte[] hashBytes = digest.digest(fileBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02x", b));
        String actualHash = sb.toString();
        if (!expectedHash.equals(actualHash)) {
            Files.deleteIfExists(file);
            throw new SecurityException("Update checksum mismatch — download rejected.");
        }
        logger.info("VoxelPort update checksum verified OK.");
    }

    private static String fileNameFromUrl(String url) {
        String path = URI.create(url).getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : "voxelport-mod-update.jar";
        if (name.isBlank() || !name.matches("[A-Za-z0-9._-]+") || name.contains("..")) {
            return "voxelport-mod-update.jar";
        }
        return name;
    }

    private static String normalizeVersion(String version) {
        String value = version == null ? "" : version.trim();
        while (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        return value;
    }

    private record Update(String version, String jarUrl, String sha256Url) {}
}
