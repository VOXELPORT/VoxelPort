package org.localm.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TunnelService {
    private Process tunnelProcess;
    private Process accessProcess;
    private ServerSocket hostProxy;
    private ServerSocket clientProxy;

    public void startRoom(int serverPort, Consumer<String> codeConsumer, Consumer<String> statusConsumer) throws Exception {
        stopRoom();
        int bridgePort = freePort();
        hostProxy = new ServerSocket();
        hostProxy.bind(new InetSocketAddress("127.0.0.1", bridgePort));
        CompletableFuture.runAsync(() -> acceptProxy(hostProxy, serverPort));

        Path daemon = daemonPath();
        tunnelProcess = new ProcessBuilder(daemon.toString(), "tunnel", "--url", "tcp://localhost:" + bridgePort).redirectErrorStream(true).start();
        CompletableFuture.runAsync(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(tunnelProcess.getInputStream()))) {
                String line;
                Pattern urlPattern = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com", Pattern.CASE_INSENSITIVE);
                while ((line = r.readLine()) != null) {
                    Matcher m = urlPattern.matcher(line);
                    if (m.find()) {
                        String code = encodeRoomUrl(m.group());
                        codeConsumer.accept(code);
                        statusConsumer.accept("Room ready");
                        monitorTunnel(serverPort, codeConsumer, statusConsumer);
                    }
                }
            } catch (Exception ignored) {}
        });
        statusConsumer.accept("Starting room...");
    }

    private void monitorTunnel(int serverPort, Consumer<String> codeConsumer, Consumer<String> statusConsumer) {
        CompletableFuture.runAsync(() -> {
            try {
                while (tunnelProcess != null && tunnelProcess.isAlive()) {
                    Thread.sleep(5000);
                }
                if (tunnelProcess != null) {
                    statusConsumer.accept("Tunnel died. Reconnecting...");
                    startRoom(serverPort, codeConsumer, statusConsumer);
                }
            } catch (Exception e) {
                statusConsumer.accept("Tunnel monitor failed: " + e.getMessage());
            }
        });
    }

    public void stopRoom() {
        closeQuiet(hostProxy);
        hostProxy = null;
        if (tunnelProcess != null) tunnelProcess.destroyForcibly();
        tunnelProcess = null;
    }

    public void startJoinProxy(String code, int localPort, Consumer<String> statusConsumer) throws Exception {
        stopJoinProxy();
        String url = decodeRoomUrl(code.trim());
        String host = URI.create(url).getHost();
        int bridgePort = freePort();
        accessProcess = new ProcessBuilder(daemonPath().toString(), "access", "tcp", "--hostname", host, "--url", "127.0.0.1:" + bridgePort).start();
        Thread.sleep(2500);

        clientProxy = new ServerSocket();
        clientProxy.bind(new InetSocketAddress("127.0.0.1", localPort));
        CompletableFuture.runAsync(() -> acceptProxy(clientProxy, bridgePort));
        statusConsumer.accept("Join proxy ready");
    }

    public void stopJoinProxy() {
        closeQuiet(clientProxy);
        clientProxy = null;
        if (accessProcess != null) accessProcess.destroyForcibly();
        accessProcess = null;
    }

    private void acceptProxy(ServerSocket server, int targetPort) {
        while (!server.isClosed()) {
            try {
                Socket incoming = server.accept();
                Socket target = new Socket("127.0.0.1", targetPort);
                CompletableFuture.runAsync(() -> pump(incoming, target));
                CompletableFuture.runAsync(() -> pump(target, incoming));
            } catch (IOException ignored) {
                return;
            }
        }
    }

    private void pump(Socket from, Socket to) {
        try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
            in.transferTo(out);
        } catch (IOException ignored) {
        } finally {
            closeQuiet(from);
            closeQuiet(to);
        }
    }

    private Path daemonPath() {
        Platform platform = detectPlatform();
        String binName = platform.localFileName();
        
        Path dev = Path.of("bin", binName).toAbsolutePath();
        if (Files.exists(dev)) return dev;
        Path app = Path.of(System.getProperty("user.dir"), "bin", binName);
        if (Files.exists(app)) return app;
        Path managed = managedToolsDir().resolve(binName);
        if (Files.exists(managed)) return managed;
        downloadTunnelDaemon(managed, platform);
        return managed;
    }

    private Path managedToolsDir() {
        String base = System.getenv("APPDATA");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home");
        }
        Path dir = Path.of(base, "VoxelPort", "tools");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create tools directory: " + dir, e);
        }
        return dir;
    }

    private void downloadTunnelDaemon(Path target, Platform platform) {
        String url = "https://github.com/cloudflare/cloudflared/releases/latest/download/" + platform.assetName();

        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile("voxelport-cloudflared-", platform.downloadExtension());
            try (InputStream in = URI.create(url).toURL().openStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (platform.isArchive()) {
                extractCloudflaredArchive(temp, target);
                Files.deleteIfExists(temp);
            } else {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!platform.isWindows()) {
                target.toFile().setExecutable(true);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download Cloudflare tunnel daemon for "
                    + platform.cloudflareOs() + "/" + platform.cloudflareArch(), e);
        }
    }

    private void extractCloudflaredArchive(Path archive, Path target) throws IOException {
        Path extractDir = Files.createTempDirectory("voxelport-cloudflared-");
        try {
            Process p = new ProcessBuilder("tar", "-xzf", archive.toAbsolutePath().toString(), "-C", extractDir.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();
            try {
                if (p.waitFor() != 0) {
                    throw new IOException("tar failed while extracting Cloudflare tunnel daemon");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Cloudflare tunnel daemon extraction interrupted", e);
            }

            try (var walk = Files.walk(extractDir)) {
                Path binary = walk
                        .filter(Files::isRegularFile)
                        .filter(candidate -> candidate.getFileName().toString().equals("cloudflared"))
                        .findFirst()
                        .orElseThrow(() -> new IOException("cloudflared binary not found in archive"));
                Files.move(binary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            deleteDirectory(extractDir);
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

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private String encodeRoomUrl(String url) {
        return "VP1-" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeRoomUrl(String code) {
        String value = code == null ? "" : code.trim();
        if (!value.startsWith("VP1-")) {
            throw new IllegalArgumentException("Invalid room code");
        }
        byte[] raw = Base64.getUrlDecoder().decode(value.substring(4));
        String url = new String(raw, StandardCharsets.UTF_8);
        if (!url.startsWith("https://") || URI.create(url).getHost() == null) {
            throw new IllegalArgumentException("Invalid room code");
        }
        return url;
    }

    private void closeQuiet(Closeable closeable) {
        try { if (closeable != null) closeable.close(); } catch (IOException ignored) {}
    }

    private Platform detectPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String archName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String os;
        boolean windows = false;
        if (osName.contains("win")) {
            os = "windows";
            windows = true;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            os = "darwin";
        } else if (osName.contains("linux")) {
            os = "linux";
        } else {
            throw new IllegalStateException("Unsupported OS for Cloudflare tunnel daemon: " + osName);
        }

        String arch = (archName.contains("aarch64") || archName.contains("arm64")) ? "arm64" : "amd64";
        return new Platform(os, arch, windows);
    }

    private record Platform(String cloudflareOs, String cloudflareArch, boolean isWindows) {
        boolean isArchive() {
            return cloudflareOs.equals("darwin");
        }

        String extension() {
            return isWindows ? ".exe" : "";
        }

        String downloadExtension() {
            return isArchive() ? ".tgz" : extension();
        }

        String assetName() {
            return "cloudflared-" + cloudflareOs + "-" + cloudflareArch + downloadExtension();
        }

        String localFileName() {
            return "cloudflared-" + cloudflareOs + "-" + cloudflareArch + extension();
        }
    }
}
