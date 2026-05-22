package com.voxelport.mod.logic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class HostingService {
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    private final Path dataFolder;
    private final Logger logger;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "voxelport-host-worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "voxelport-host-ping");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private volatile WebSocket controlSocket;
    private volatile HostSession session;
    private final ConcurrentHashMap<String, BridgePair> bridges = new ConcurrentHashMap<>();
    private final AtomicInteger playerCount = new AtomicInteger(0);
    private final AtomicLong relayPingMs = new AtomicLong(-1);
    private final AtomicLong lastPingSentNs = new AtomicLong(0);
    private volatile ScheduledFuture<?> pingTask;

    public HostingService(Path dataFolder, Logger logger) {
        this.dataFolder = Objects.requireNonNull(dataFolder);
        this.logger = Objects.requireNonNull(logger);
    }

    public synchronized HostSession start(int gamePort) throws Exception {
        if (session != null) {
            throw new IllegalStateException("A VoxelPort host session is already running.");
        }

        CountDownLatch codeLatch = new CountDownLatch(1);
        AtomicReference<String> roomCodeRef = new AtomicReference<>();
        AtomicReference<String> startErrorRef = new AtomicReference<>();

        WebSocket ws;
        try {
            ws = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(RelayUrlResolver.get()), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            webSocket.request(1);
                            handleControlMessage(webSocket, data.toString(), roomCodeRef, startErrorRef, codeLatch, gamePort);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                            webSocket.request(1);
                            long sentNs = lastPingSentNs.get();
                            if (sentNs > 0) {
                                relayPingMs.set((System.nanoTime() - sentNs) / 1_000_000L);
                            }
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            startErrorRef.compareAndSet(null, error.getMessage());
                            codeLatch.countDown();
                            if (isRunning()) stop();
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            codeLatch.countDown();
                            if (isRunning()) {
                                logger.warn("VoxelPort relay control socket closed: {} ({})", reason, statusCode);
                                stop();
                            }
                            return null;
                        }
                    })
                    .get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IOException("Failed to connect to VoxelPort relay: " + e.getCause().getMessage(), e.getCause());
        }

        ws.sendText("{\"type\":\"create\"}", true);

        if (!codeLatch.await(15, TimeUnit.SECONDS)) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "timeout");
            throw new IOException("Timed out waiting for relay to assign a room code.");
        }

        String error = startErrorRef.get();
        if (error != null) {
            throw new IOException("Relay error: " + error);
        }

        String code = roomCodeRef.get();
        if (code == null || !ROOM_CODE_PATTERN.matcher(code).matches()) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bad code");
            throw new IOException("Relay returned an invalid room code: " + code);
        }

        try {
            Files.createDirectories(dataFolder);
            setSecureDirectoryPermissions(dataFolder);
            Path codeFile = dataFolder.resolve("last_code.txt");
            Files.writeString(codeFile, code, StandardCharsets.UTF_8);
            setSecureFilePermissions(codeFile);
        } catch (IOException e) {
            logger.warn("Failed to save session code: {}", e.getMessage());
        }

        this.controlSocket = ws;
        this.session = new HostSession(gamePort, code, Instant.now());
        relayPingMs.set(-1);

        // Start periodic relay ping measurements
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            WebSocket sock = controlSocket;
            if (sock == null || sock.isOutputClosed()) return;
            try {
                lastPingSentNs.set(System.nanoTime());
                sock.sendPing(ByteBuffer.allocate(0));
            } catch (Exception ignored) {}
        }, 1, 3, TimeUnit.SECONDS);

        logger.info("VoxelPort host session started. Room code: {} on server port {}", code, gamePort);
        return this.session;
    }

    public synchronized void stop() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        WebSocket ws = this.controlSocket;
        if (ws != null) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "Session ended"); } catch (Exception ignored) {}
            this.controlSocket = null;
        }
        for (BridgePair pair : bridges.values()) {
            try { pair.ws().sendClose(WebSocket.NORMAL_CLOSURE, "Session ended"); } catch (Exception ignored) {}
            try { pair.tcp().close(); } catch (IOException ignored) {}
        }
        bridges.clear();
        session = null;
        playerCount.set(0);
        relayPingMs.set(-1);
    }

    public synchronized boolean isRunning() { return session != null; }
    public synchronized HostSession getSession() { return session; }
    public int getPlayerCount() { return playerCount.get(); }
    public long getRelayPingMs() { return relayPingMs.get(); }

    private void handleControlMessage(WebSocket ws, String data,
                                      AtomicReference<String> roomCodeRef,
                                      AtomicReference<String> startErrorRef,
                                      CountDownLatch codeLatch, int gamePort) {
        try {
            JsonObject obj = JsonParser.parseString(data).getAsJsonObject();

            if (obj.has("error")) {
                startErrorRef.compareAndSet(null, obj.get("error").getAsString());
                codeLatch.countDown();
                return;
            }

            if (obj.has("code") && codeLatch.getCount() > 0) {
                String code = obj.get("code").getAsString().toUpperCase();
                if (ROOM_CODE_PATTERN.matcher(code).matches()) {
                    roomCodeRef.set(code);
                    codeLatch.countDown();
                    return;
                }
            }

            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            switch (type) {
                case "player-join-request" -> {
                    String playerId = obj.has("playerId") ? obj.get("playerId").getAsString() : null;
                    if (playerId != null && isRunning()) {
                        executor.submit(() -> createBridgeForPlayer(playerId, gamePort));
                    }
                }
                case "player-joined" -> {
                    if (obj.has("playerCount")) playerCount.set(obj.get("playerCount").getAsInt());
                }
                case "player-left" -> {
                    if (obj.has("playerCount")) playerCount.set(obj.get("playerCount").getAsInt());
                }
            }
        } catch (Exception e) {
            logger.warn("VoxelPort: Failed to parse relay control message: {}", e.getMessage());
            startErrorRef.compareAndSet(null, e.getMessage());
            codeLatch.countDown();
        }
    }

    private void createBridgeForPlayer(String playerId, int gamePort) {
        HostSession s = session;
        if (s == null) return;
        String code = s.getCode();

        Socket tcpSocket;
        try {
            tcpSocket = new Socket("127.0.0.1", gamePort);
            tcpSocket.setTcpNoDelay(true);
        } catch (IOException e) {
            logger.error("VoxelPort: Cannot connect bridge to game server port {}: {}", gamePort, e.getMessage());
            return;
        }

        CountDownLatch pairedLatch = new CountDownLatch(1);
        AtomicBoolean cleanedUp = new AtomicBoolean(false);

        try {
            WebSocket bridgeWs = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(RelayUrlResolver.get()), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket ws) { ws.request(1); }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            ws.request(1);
                            try {
                                JsonObject obj = JsonParser.parseString(data.toString()).getAsJsonObject();
                                String status = obj.has("status") ? obj.get("status").getAsString() : "";
                                if ("paired".equals(status)) pairedLatch.countDown();
                                else if (obj.has("error")) {
                                    pairedLatch.countDown();
                                    cleanupBridge(playerId, tcpSocket, ws, cleanedUp);
                                }
                            } catch (Exception e) {
                                pairedLatch.countDown();
                                cleanupBridge(playerId, tcpSocket, ws, cleanedUp);
                            }
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                            ws.request(1);
                            try {
                                byte[] bytes = new byte[data.remaining()];
                                data.get(bytes);
                                tcpSocket.getOutputStream().write(bytes);
                                tcpSocket.getOutputStream().flush();
                            } catch (IOException e) {
                                cleanupBridge(playerId, tcpSocket, ws, cleanedUp);
                            }
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            pairedLatch.countDown();
                            cleanupBridge(playerId, tcpSocket, ws, cleanedUp);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            pairedLatch.countDown();
                            cleanupBridge(playerId, tcpSocket, ws, cleanedUp);
                            return null;
                        }
                    })
                    .get(15, TimeUnit.SECONDS);

            bridges.put(playerId, new BridgePair(bridgeWs, tcpSocket));

            JsonObject pairMsg = new JsonObject();
            pairMsg.addProperty("type", "host-pair");
            pairMsg.addProperty("code", code);
            pairMsg.addProperty("playerId", playerId);
            bridgeWs.sendText(pairMsg.toString(), true);

            if (!pairedLatch.await(15, TimeUnit.SECONDS)) {
                logger.warn("VoxelPort: Timed out waiting for relay to pair player {}", playerId);
                cleanupBridge(playerId, tcpSocket, bridgeWs, cleanedUp);
                return;
            }
            if (cleanedUp.get()) return;

            executor.submit(() -> {
                try (InputStream in = tcpSocket.getInputStream()) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1 && !cleanedUp.get()) {
                        if (bridgeWs.isOutputClosed()) break;
                        bridgeWs.sendBinary(ByteBuffer.wrap(buffer, 0, read), true);
                    }
                } catch (Exception e) {
                    if (!cleanedUp.get()) logger.debug("VoxelPort: Bridge TCP→WS ended for player {}: {}", playerId, e.getMessage());
                } finally {
                    cleanupBridge(playerId, tcpSocket, bridgeWs, cleanedUp);
                }
            });

        } catch (Exception e) {
            logger.error("VoxelPort: Failed to create bridge for player {}: {}", playerId, e.getMessage());
            bridges.remove(playerId);
            try { tcpSocket.close(); } catch (IOException ignored) {}
        }
    }

    private void cleanupBridge(String playerId, Socket tcpSocket, WebSocket ws, AtomicBoolean cleanedUp) {
        if (cleanedUp.compareAndSet(false, true)) {
            bridges.remove(playerId);
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "Done"); } catch (Exception ignored) {}
            try { tcpSocket.close(); } catch (IOException ignored) {}
        }
    }

    private static void setSecureDirectoryPermissions(Path path) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (Exception ignored) {}
    }

    private static void setSecureFilePermissions(Path path) {
        try {
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix"))
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (Exception ignored) {}
    }

    private record BridgePair(WebSocket ws, Socket tcp) {}

    public static final class HostSession {
        private final int gamePort;
        private final String code;
        private final Instant startedAt;

        HostSession(int gamePort, String code, Instant startedAt) {
            this.gamePort = gamePort;
            this.code = code;
            this.startedAt = startedAt;
        }

        public int getGamePort() { return gamePort; }
        public String getCode() { return code; }

        public String getUptimeLabel() {
            Duration d = Duration.between(startedAt, Instant.now());
            long h = d.toHours(), m = d.toMinutesPart(), s = d.toSecondsPart();
            if (h > 0) return h + "h " + m + "m";
            if (m > 0) return m + "m " + s + "s";
            return s + "s";
        }
    }
}
