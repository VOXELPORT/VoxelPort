package org.localm.service;

import org.localm.model.ServerVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionService {
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public List<ServerVersion> fetchVersions() {
        List<ServerVersion> versions = new ArrayList<>();
        // Paper
        try {
            String paper = httpGet("https://api.papermc.io/v2/projects/paper");
            for (String version : lastVersions(paper)) {
                String buildsJson = httpGet("https://api.papermc.io/v2/projects/paper/versions/" + version + "/builds");
                String build = lastNumber(buildsJson, "\"build\"\\s*:\\s*(\\d+)");
                String file = last(buildsJson, "\"name\"\\s*:\\s*\"([^\"]+\\.jar)\"");
                if (build != null && file != null) {
                    versions.add(new ServerVersion("Paper " + version, version, "https://api.papermc.io/v2/projects/paper/versions/" + version + "/builds/" + build + "/downloads/" + file));
                }
            }
        } catch (Exception ignored) {}
        // Purpur
        try {
            String purpur = httpGet("https://api.purpurmc.org/v2/purpur/");
            for (String version : lastVersions(purpur)) {
                versions.add(new ServerVersion("Purpur " + version, version, "https://api.purpurmc.org/v2/purpur/" + version + "/latest/download"));
            }
        } catch (Exception ignored) {}
        // Fabric
        try {
            String fabricJson = httpGet("https://meta.fabricmc.net/v2/versions/game");
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"(1\\.\\d+(?:\\.\\d+)?)\",\\s*\"stable\"\\s*:\\s*true").matcher(fabricJson);
            List<String> stableVersions = new ArrayList<>();
            while (m.find()) stableVersions.add(m.group(1));
            for (int i = 0; i < Math.min(5, stableVersions.size()); i++) {
                String v = stableVersions.get(i);
                versions.add(new ServerVersion("Fabric " + v, v, "https://meta.fabricmc.net/v2/versions/loader/" + v + "/0.15.11/1.0.1/server/jar"));
            }
        } catch (Exception ignored) {}

        // Forge
        try {
            String forgeJson = httpGet("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");
            // The JSON is a bit flat, let's use regex to find recommended versions
            // Pattern: "1.20.1-recommended": "47.2.0"
            Matcher m = Pattern.compile("\"(\\d+\\.\\d+(?:\\.\\d+)?)-recommended\"\\s*:\\s*\"([^\"]+)\"").matcher(forgeJson);
            List<Map.Entry<String, String>> forgePromos = new ArrayList<>();
            while (m.find()) {
                forgePromos.add(Map.entry(m.group(1), m.group(2)));
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

    public void download(String url, Path target) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "VoxelPort/0.1").build();
        HttpResponse<byte[]> response = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed with HTTP " + response.statusCode());
        }
        byte[] body = response.body();
        if (body.length < 4 || body[0] != 'P' || body[1] != 'K') {
            String preview = new String(body, 0, Math.min(body.length, 160), StandardCharsets.UTF_8);
            throw new IOException("Downloaded file is not a jar: " + preview);
        }
        Files.write(target, body);
    }

    private List<String> lastVersions(String json) {
        Matcher matcher = Pattern.compile("\"(1\\.\\d+(?:\\.\\d+)?)\"").matcher(json);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        while (matcher.find()) values.add(matcher.group(1));
        List<String> list = new ArrayList<>(values);
        Collections.reverse(list);
        return list.subList(0, Math.min(12, list.size()));
    }

    private String last(String input, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        return last;
    }

    private String lastNumber(String input, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        return last;
    }
}
