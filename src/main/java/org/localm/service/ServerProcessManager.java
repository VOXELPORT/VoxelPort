package org.localm.service;

import java.io.*;
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

    public ServerProcessManager(Path dataDir) {
        this.logDir = dataDir.resolve("logs");
        try {
            Files.createDirectories(logDir);
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
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String target = isWindows ? "win_args.txt" : "unix_args.txt";
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
            
            // To keep it dependency-free and simple, we'll try to use 'tasklist' on Windows or 'ps' on Linux
            // if we want more detailed info. But let's see if we can get anything from ProcessHandle.
            
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
        List<String> candidates = new ArrayList<>();
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String bin = isWindows ? "java.exe" : "java";

        candidates.add(bin);
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(Path.of(javaHome, "bin", bin).toString());
        }
        candidates.add(Path.of(System.getProperty("java.home"), "bin", bin).toString());

        for (String candidate : candidates) {
            int major = getJavaMajor(candidate);
            if (major >= required) return candidate;
        }

        throw new IllegalStateException("Minecraft " + mcVersion + " needs Java " + required + "+. Install Java 21 or newer.");
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
