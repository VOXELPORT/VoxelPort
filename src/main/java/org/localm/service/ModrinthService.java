package org.localm.service;

import org.localm.model.ModrinthProject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModrinthService {
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public List<ModrinthProject> search(String query, String type) throws IOException, InterruptedException {
        String url = "https://api.modrinth.com/v2/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        if (type != null) {
            url += "&facets=" + URLEncoder.encode("[[\"project_type:" + type + "\"]]", StandardCharsets.UTF_8);
        }
        
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "VoxelPort/0.1").build();
        String json = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        
        List<ModrinthProject> projects = new ArrayList<>();
        // Simple regex to parse search results
        // Pattern: {"project_id":"...","slug":"...","author":"...","title":"...","description":"..."}
        Matcher m = Pattern.compile("\\{\"project_id\":\"([^\"]+)\",\"slug\":\"([^\"]+)\",\"author\":\"([^\"]+)\",\"title\":\"([^\"]+)\",\"description\":\"([^\"]+)\"").matcher(json);
        while (m.find()) {
            projects.add(new ModrinthProject(m.group(1), m.group(2), m.group(4), m.group(5), m.group(3)));
        }
        return projects;
    }

    public String getLatestDownloadUrl(String projectId, String mcVersion, String loader) throws IOException, InterruptedException {
        String url = "https://api.modrinth.com/v2/project/" + projectId + "/version";
        url += "?game_versions=" + URLEncoder.encode("[\"" + mcVersion + "\"]", StandardCharsets.UTF_8);
        url += "&loaders=" + URLEncoder.encode("[\"" + loader.toLowerCase() + "\"]", StandardCharsets.UTF_8);
        
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "VoxelPort/0.1").build();
        String json = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
        
        // Find the first file URL
        Matcher m = Pattern.compile("\"url\":\"([^\"]+\\.jar)\"").matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
