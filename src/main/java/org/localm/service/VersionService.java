package org.localm.service;

import org.localm.model.ServerVersion;
import org.localm.util.SimpleJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class VersionService {
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private String fabricLoaderVersion;
    private String fabricInstallerVersion;

    public List<ServerVersion> fetchVersions() {
        List<ServerVersion> versions = new ArrayList<>();
        // Paper
        try {
            for (String version : recentVersions(httpGet("https://api.papermc.io/v2/projects/paper"), 12)) {
                String url = latestPaperDownloadUrl(version);
                if (url != null) {
                    versions.add(new ServerVersion("Paper " + version, version, url));
                }
            }
        } catch (Exception ignored) {}
        // Purpur
        try {
            for (String version : recentVersions(httpGet("https://api.purpurmc.org/v2/purpur/"), 12)) {
                versions.add(new ServerVersion("Purpur " + version, version, "https://api.purpurmc.org/v2/purpur/" + version + "/latest/download"));
            }
        } catch (Exception ignored) {}
        // Fabric
        try {
            List<String> stableVersions = new ArrayList<>();
            for (Object item : SimpleJson.asArray(SimpleJson.parse(httpGet("https://meta.fabricmc.net/v2/versions/game")))) {
                Map<String, Object> obj = SimpleJson.asObject(item);
                String version = SimpleJson.asString(obj.get("version"));
                if (version != null && version.startsWith("1.") && SimpleJson.asBoolean(obj.get("stable"), false)) {
                    stableVersions.add(version);
                }
            }
            for (int i = 0; i < Math.min(5, stableVersions.size()); i++) {
                String v = stableVersions.get(i);
                versions.add(new ServerVersion("Fabric " + v, v, fabricServerJarUrl(v)));
            }
        } catch (Exception ignored) {}

        // Forge
        try {
            List<Map.Entry<String, String>> forgePromos = new ArrayList<>();
            Map<String, Object> root = SimpleJson.asObject(SimpleJson.parse(httpGet("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")));
            Map<String, Object> promos = SimpleJson.asObject(root.get("promos"));
            for (Map.Entry<String, Object> entry : promos.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("-recommended")) {
                    forgePromos.add(Map.entry(key.substring(0, key.length() - "-recommended".length()), SimpleJson.asString(entry.getValue())));
                }
            }
            Collections.reverse(forgePromos);
            for (int i = 0; i < Math.min(6, forgePromos.size()); i++) {
                String mcV = forgePromos.get(i).getKey();
                String fV = forgePromos.get(i).getValue();
                String url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + mcV + "-" + fV + "/forge-" + mcV + "-" + fV + "-installer.jar";
                versions.add(new ServerVersion("Forge " + mcV, mcV, url));
            }
        } catch (Exception ignored) {}
        return versions;
    }

    public String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "VoxelPort/0.1").build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public String latestPaperDownloadUrl(String mcVersion) throws IOException, InterruptedException {
        String buildsJson = httpGet("https://api.papermc.io/v2/projects/paper/versions/" + mcVersion + "/builds");
        List<Object> builds = SimpleJson.asArray(SimpleJson.asObject(SimpleJson.parse(buildsJson)).get("builds"));
        if (builds.isEmpty()) return null;
        Map<String, Object> latest = SimpleJson.asObject(builds.get(builds.size() - 1));
        long build = SimpleJson.asLong(latest.get("build"), -1);
        Map<String, Object> downloads = SimpleJson.asObject(latest.get("downloads"));
        Map<String, Object> application = SimpleJson.asObject(downloads.get("application"));
        String file = SimpleJson.asString(application.get("name"));
        if (build < 0 || file == null) return null;
        return "https://api.papermc.io/v2/projects/paper/versions/" + mcVersion + "/builds/" + build + "/downloads/" + file;
    }

    private String fabricServerJarUrl(String mcVersion) throws IOException, InterruptedException {
        return "https://meta.fabricmc.net/v2/versions/loader/"
                + mcVersion + "/"
                + latestFabricLoaderVersion() + "/"
                + latestFabricInstallerVersion() + "/server/jar";
    }

    private String latestFabricLoaderVersion() throws IOException, InterruptedException {
        if (fabricLoaderVersion == null) {
            fabricLoaderVersion = latestVersionFromMetaArray("https://meta.fabricmc.net/v2/versions/loader");
        }
        return fabricLoaderVersion;
    }

    private String latestFabricInstallerVersion() throws IOException, InterruptedException {
        if (fabricInstallerVersion == null) {
            fabricInstallerVersion = latestVersionFromMetaArray("https://meta.fabricmc.net/v2/versions/installer");
        }
        return fabricInstallerVersion;
    }

    private String latestVersionFromMetaArray(String url) throws IOException, InterruptedException {
        for (Object item : SimpleJson.asArray(SimpleJson.parse(httpGet(url)))) {
            Map<String, Object> obj = SimpleJson.asObject(item);
            String version = SimpleJson.asString(obj.get("version"));
            if (version != null && !version.isBlank()) {
                return version;
            }
        }
        throw new IOException("No versions returned by " + url);
    }

    public void download(String url, Path target) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "VoxelPort/0.1").build();
        HttpResponse<byte[]> response = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed with HTTP " + response.statusCode() + ": " + url);
        }
        byte[] body = response.body();
        if (body.length < 4 || body[0] != 'P' || body[1] != 'K') {
            String preview = new String(body, 0, Math.min(body.length, 160), StandardCharsets.UTF_8);
            throw new IOException("Downloaded file is not a jar: " + preview);
        }
        Files.write(target, body);
    }

    private List<String> recentVersions(String json, int limit) {
        Object parsed = SimpleJson.parse(json);
        Object versionValue = SimpleJson.asObject(parsed).get("versions");
        List<Object> raw = versionValue instanceof List<?> ? SimpleJson.asArray(versionValue) : SimpleJson.asArray(parsed);
        List<String> list = new ArrayList<>();
        for (Object item : raw) {
            String version = SimpleJson.asString(item);
            if (version != null && version.startsWith("1.")) {
                list.add(version);
            }
        }
        Collections.reverse(list);
        return list.subList(0, Math.min(limit, list.size()));
    }
}
