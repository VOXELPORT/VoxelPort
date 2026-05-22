package com.voxelport.mod.logic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class JoinService {
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$");

    private final Path configDir;
    private final Logger logger;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "voxelport-join-worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "voxelport-join-ping");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .executor(executor)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private volatile ServerSocket proxySocket;
    private volatile boolean running;
    // Most recent relay WebSocket latency (ms); -1 = not yet measured
    private final AtomicLong relayPingMs = new AtomicLong(-1);
    // Reference to the active relay WebSocket for ping scheduling
    private volatile WebSocket activeRelayWs;
    private final AtomicLong lastPingSentNs = new AtomicLong(0);
    private volatile ScheduledFuture<?> pingTask;

    public JoinService(Path configDir, Logger logger) {
        this.configDir = Objects.requireNonNull(configDir);
        this.logger = Objects.requireNonNull(logger);
    }

    public synchronized int startProxy(String code) throws Exception {
        if (running) stopProxy();

        String normalizedCode = code.trim().toUpperCase();
        if (!ROOM_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new IllegalArgumentException("Invalid room code. Expected 6 uppercase alphanumeric characters.");
        }

        proxySocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        running = true;
        relayPingMs.set(-1);

        executor.submit(() -> acceptLoop(normalizedCode));
        return proxySocket.getLocalPort();
    }

    public synchronized void stopProxy() {
        running = false;
        if (pingTask != null) { pingTask.cancel(false); pingTask = null; }
        activeRelayWs = null;
        if (proxySocket != null) {
            try { proxySocket.close(); } catch (IOException ignored) {}
            proxySocket = null;
        }
        relayPingMs.set(-1);
    }

    public synchronized boolean isRunning() { return running; }
    public long getRelayPingMs() { return relayPingMs.get(); }

    private void acceptLoop(String code) {
        while (running) {
            try {
                Socket client = proxySocket.accept();
                executor.submit(() -> {
                    try {
                        doProxy(client, code);
                    } catch (Exception e) {
                        if (!client.isClosed()) logger.error("VoxelPort join proxy error: {}", e.getMessage());
                        try { client.close(); } catch (IOException ignored) {}
                    }
                });
            } catch (IOException e) {
                if (running) logger.error("VoxelPort: Proxy accept error: {}", e.getMessage());
                break;
            }
        }
    }

    private void doProxy(Socket tcpClient, String code) throws Exception {
        tcpClient.setTcpNoDelay(true);

        CountDownLatch joiningLatch = new CountDownLatch(1);
        AtomicBoolean relayReady = new AtomicBoolean(false);
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        AtomicReference<String> errorRef = new AtomicReference<>();
        Queue<byte[]> pendingChunks = new ConcurrentLinkedQueue<>();

        WebSocket relayWs;
        try {
            relayWs = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(RelayUrlResolver.get()), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket ws) { ws.request(1); }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            ws.request(1);
                            try {
                                JsonObject obj = JsonParser.parseString(data.toString()).getAsJsonObject();
                                if (obj.has("error")) {
                                    errorRef.set(obj.get("error").getAsString());
                                    joiningLatch.countDown();
                                    cleanupJoin(tcpClient, ws, cleanedUp);
                                    return null;
                                }
                                String status = obj.has("status") ? obj.get("status").getAsString() : "";
                                if ("joining".equals(status)) {
                                    joiningLatch.countDown();
                                } else if ("paired".equals(status)) {
                                    relayReady.set(true);
                                    byte[] chunk;
                                    while ((chunk = pendingChunks.poll()) != null && !ws.isOutputClosed()) {
                                        ws.sendBinary(ByteBuffer.wrap(chunk), true);
                                    }
                                }
                            } catch (Exception e) {
                                errorRef.set(e.getMessage());
                                joiningLatch.countDown();
                                cleanupJoin(tcpClient, ws, cleanedUp);
                            }
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                            ws.request(1);
                            try {
                                byte[] bytes = new byte[data.remaining()];
                                data.get(bytes);
                                tcpClient.getOutputStream().write(bytes);
                                tcpClient.getOutputStream().flush();
                            } catch (IOException e) {
                                cleanupJoin(tcpClient, ws, cleanedUp);
                            }
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onPong(WebSocket ws, ByteBuffer message) {
                            ws.request(1);
                            long sentNs = lastPingSentNs.get();
                            if (sentNs > 0) relayPingMs.set((System.nanoTime() - sentNs) / 1_000_000L);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            errorRef.set(error.getMessage());
                            joiningLatch.countDown();
                            cleanupJoin(tcpClient, ws, cleanedUp);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            joiningLatch.countDown();
                            cleanupJoin(tcpClient, ws, cleanedUp);
                            return null;
                        }
                    })
                    .get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IOException("Failed to connect to VoxelPort relay: " + e.getCause().getMessage(), e.getCause());
        }

        // Register as active relay socket for ping scheduling
        activeRelayWs = relayWs;
        if (pingTask == null || pingTask.isDone()) {
            pingTask = scheduler.scheduleAtFixedRate(() -> {
                WebSocket ws = activeRelayWs;
                if (ws == null || ws.isOutputClosed()) return;
                try {
                    lastPingSentNs.set(System.nanoTime());
                    ws.sendPing(ByteBuffer.allocate(0));
                } catch (Exception ignored) {}
            }, 2, 3, TimeUnit.SECONDS);
        }

        JsonObject joinMsg = new JsonObject();
        joinMsg.addProperty("type", "join");
        joinMsg.addProperty("code", code);
        relayWs.sendText(joinMsg.toString(), true);

        if (!joiningLatch.await(15, TimeUnit.SECONDS)) {
            cleanupJoin(tcpClient, relayWs, cleanedUp);
            throw new IOException("Relay did not accept the room code in time. Is the room still open?");
        }

        String err = errorRef.get();
        if (err != null) {
            cleanupJoin(tcpClient, relayWs, cleanedUp);
            throw new IOException("Relay rejected the join: " + err);
        }

        executor.submit(() -> {
            try (InputStream in = tcpClient.getInputStream()) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1 && !cleanedUp.get()) {
                    if (!relayReady.get()) {
                        byte[] copy = new byte[read];
                        System.arraycopy(buffer, 0, copy, 0, read);
                        pendingChunks.offer(copy);
                    } else if (!relayWs.isOutputClosed()) {
                        relayWs.sendBinary(ByteBuffer.wrap(buffer, 0, read), true);
                    }
                }
            } catch (Exception e) {
                if (!cleanedUp.get()) logger.debug("VoxelPort: Join TCP stream ended: {}", e.getMessage());
            } finally {
                cleanupJoin(tcpClient, relayWs, cleanedUp);
            }
        });
    }

    private void cleanupJoin(Socket tcpClient, WebSocket ws, AtomicBoolean cleanedUp) {
        if (cleanedUp.compareAndSet(false, true)) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "Done"); } catch (Exception ignored) {}
            try { tcpClient.close(); } catch (IOException ignored) {}
        }
    }
}
