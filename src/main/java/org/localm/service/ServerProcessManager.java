package org.localm.service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerProcessManager {
    private final Map<String, Process> activeServers = new ConcurrentHashMap<>();
    private final Path logDir;
    private final Path toolsDir;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public ServerProcessManager(Path dataDir) {
        this.logDir = dataDir.resolve("logs");
        this.toolsDir = dataDir.resolve("tools");
        try {
            Files.createDirectories(logDir);
            Files.createDirectories(toolsDir);
        } catch (IOException ignored) {}
    }

    public void startServer(String name, Path dir, String mcVersion, int ram, BiConsumer<String, String> logConsumer, Runnable onStop) throws IOException {
        if (activeServers.containsKey(name) && activeServers.get(name).isAlive()) {
            throw new IllegalStateException("Server already running");
        }

        String javaBin = detectJava(mcVersion);
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Xmx" + ram + "M");
        cmd.add("-Xms" + Math.max(512, ram / 2) + "M");

        Path userArgs = dir.resolve("user_jvm_args.txt");
        Path forgeArgs = findForgeArgsFile(dir);

        if (Files.exists(userArgs) && forgeArgs != null) {
            cmd.add("@user_jvm_args.txt");
            cmd.add("@" + dir.relativize(forgeArgs).toString().replace("\\", "/"));
        } else {
            cmd.add("-jar");
            cmd.add("server.jar");
            cmd.add("--nogui");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        activeServers.put(name, p);

        pipeConsole(name, p.getInputStream(), logConsumer, () -> {
            activeServers.remove(name);
            if (onStop != null) onStop.run();
        });
    }

    private Path findForgeArgsFile(Path dir) {
        Path libs = dir.resolve("libraries");
        if (!Files.exists(libs)) return null;
        String target = "win_args.txt";
        try (var walk = Files.walk(libs, 8)) {
            return walk.filter(p -> p.getFileName().toString().equals(target)).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public void stopServer(String name) {
        sendCommand(name, "stop");
    }

    public void sendCommand(String name, String command) {
        Process p = activeServers.get(name);
        if (p != null && p.isAlive()) {
            try {
                p.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().flush();
            } catch (IOException ignored) {}
        }
    }

    public void stopAll() {
        activeServers.keySet().forEach(this::stopServer);
    }

    public boolean isAlive(String name) {
        Process p = activeServers.get(name);
        return p != null && p.isAlive();
    }

    public String getProcessStats(String name) {
        Process p = activeServers.get(name);
        if (p == null || !p.isAlive()) return "";

        try {
            ProcessHandle.Info info = p.toHandle().info();
            // Note: ProcessHandle doesn't give precise RAM/CPU easily in pure Java without JNA/OS-specific calls
            // But we can get CPU duration. For RAM, we'll have to use a workaround or stick to CPU for now.
            // Actually, ProcessHandle.info().totalCpuDuration() is available.
            
            // To keep it dependency-free and simple, we use ProcessHandle only.
            
            Optional<Instant> start = info.startInstant();
            Optional<java.time.Duration> cpu = info.totalCpuDuration();
            
            if (cpu.isPresent() && start.isPresent()) {
                long cpuMillis = cpu.get().toMillis();
                long uptimeMillis = Instant.now().toEpochMilli() - start.get().toEpochMilli();
                double cpuUsage = (double) cpuMillis / uptimeMillis * 100.0;
                // This is an average since start, not real-time. 
                // For real-time we'd need to sample twice.
                return String.format("%.1f%% CPU", cpuUsage);
            }
        } catch (Exception ignored) {}
        return "Online";
    }

    private void pipeConsole(String name, InputStream in, BiConsumer<String, String> logConsumer, Runnable onComplete) {
        Path logFile = logDir.resolve(name + ".log");
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                 BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, 
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logConsumer.accept(name, line);
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException ignored) {
            } finally {
                onComplete.run();
            }
        });
    }

    public String detectJava(String mcVersion) {
        int required = requiredJava(mcVersion);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            throw new IllegalStateException("VoxelPort first release is Windows-only.");
        }
        List<String> candidates = new ArrayList<>();
        String bin = "java.exe";

        candidates.add(bin);
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(Path.of(javaHome, "bin", bin).toString());
        }
        candidates.add(Path.of(System.getProperty("java.home"), "bin", bin).toString());

        // Check already managed runtimes first so we don't redownload.
        Path managedRoot = toolsDir.resolve("java");
        if (Files.exists(managedRoot)) {
            try (var stream = Files.list(managedRoot)) {
                stream.filter(Files::isDirectory).forEach(dir -> {
                    Path javaBin = dir.resolve("bin").resolve("java.exe");
                    if (Files.exists(javaBin)) {
                        candidates.add(javaBin.toString());
                    }
                });
            } catch (IOException ignored) {}
        }

        for (String candidate : candidates) {
            int major = getJavaMajor(candidate);
            if (major >= required) return candidate;
        }

        // Auto-bootstrap Java for end users when required version is missing.
        Path downloaded = ensureManagedJava(required);
        if (downloaded != null) {
            return downloaded.toString();
        }

        throw new IllegalStateException("Minecraft " + mcVersion + " needs Java " + required + "+. Install Java 21 or newer.");
    }

    private Path ensureManagedJava(int requiredMajor) {
        if (requiredMajor <= 0) return null;
        try {
            Path javaRoot = toolsDir.resolve("java");
            Files.createDirectories(javaRoot);
            Path targetDir = javaRoot.resolve("temurin-" + requiredMajor);
            Path javaExe = targetDir.resolve("bin").resolve("java.exe");
            if (Files.exists(javaExe)) return javaExe;

            Path zip = Files.createTempFile("voxelport-java-", ".zip");
            try {
                String url = "https://api.adoptium.net/v3/binary/latest/" + requiredMajor
                        + "/ga/windows/x64/jre/hotspot/normal/eclipse";
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "VoxelPort/1.0.0")
                        .build();
                HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(zip));
                if (res.statusCode() < 200 || res.statusCode() >= 300) {
                    throw new IOException("Java download failed with HTTP " + res.statusCode());
                }
                unzip(zip, javaRoot);
            } finally {
                Files.deleteIfExists(zip);
            }

            // Adoptium zips extract into versioned folder. Normalize it to temurin-{major}.
            if (!Files.exists(javaExe)) {
                try (var stream = Files.list(javaRoot)) {
                    Path extracted = stream
                            .filter(Files::isDirectory)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains(String.valueOf(requiredMajor)))
                            .filter(p -> Files.exists(p.resolve("bin").resolve("java.exe")))
                            .findFirst()
                            .orElse(null);
                    if (extracted != null && !extracted.equals(targetDir)) {
                        if (Files.exists(targetDir)) {
                            deleteDirectory(targetDir);
                        }
                        Files.move(extracted, targetDir);
                    }
                }
            }
            return Files.exists(javaExe) ? javaExe : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void unzip(Path zipFile, Path destinationDir) throws IOException {
        try (var zipIn = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                Path resolved = destinationDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(destinationDir)) {
                    throw new IOException("Blocked zip slip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zipIn, resolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zipIn.closeEntry();
            }
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private int requiredJava(String mcVersion) {
        Matcher matcher = Pattern.compile("1\\.(\\d+)(?:\\.(\\d+))?").matcher(mcVersion == null ? "" : mcVersion);
        if (!matcher.find()) return 21;
        int minor = Integer.parseInt(matcher.group(1));
        int patch = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        if (minor > 20 || (minor == 20 && patch >= 5)) return 21;
        if (minor >= 17) return 17;
        return 8;
    }

    private int getJavaMajor(String javaBin) {
        try {
            Process process = new ProcessBuilder(javaBin, "-version").redirectErrorStream(true).start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            process.waitFor();
            Matcher matcher = Pattern.compile("version\\s+\"(\\d+)(?:\\.(\\d+))?").matcher(output);
            if (!matcher.find()) return -1;
            int first = Integer.parseInt(matcher.group(1));
            if (first == 1 && matcher.group(2) != null) return Integer.parseInt(matcher.group(2));
            return first;
        } catch (Exception ignored) {
            return -1;
        }
    }
}
