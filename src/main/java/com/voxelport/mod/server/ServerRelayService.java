package com.voxelport.mod.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ServerRelayService {
    private static final int MAX_FRAME_BASE64_CHARS = 2 * 1024 * 1024;
    private static final int MAX_DECODED_FRAME_BYTES = (MAX_FRAME_BASE64_CHARS / 4) * 3;
    private static final int LOCAL_CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_BUFFER_BYTES = 65536;

    private final Logger logger;
    // I5 fix: bounded thread pool — prevents thread exhaustion under a connection flood
    private final ExecutorService ioPool = new ThreadPoolExecutor(
            4, 512, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "voxelport-server-relay-io");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "voxelport-server-relay-ping");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .executor(ioPool)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ConcurrentHashMap<String, PlayerConn> connections = new ConcurrentHashMap<>();
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicLong totalConnections = new AtomicLong();
    private final AtomicLong bytesFromServer = new AtomicLong();
    private final AtomicLong bytesToServer = new AtomicLong();
    private final AtomicLong relayPingMs = new AtomicLong(-1);
    private volatile java.util.concurrent.Semaphore connectionSlots;

    private final LinkedBlockingQueue<String> sendQueue = new LinkedBlockingQueue<>(8192);
    private volatile Thread senderThread;

    private volatile WebSocket socket;
    private volatile Session session;
    private volatile Config config;
    private volatile ScheduledFuture<?> pingTask;
    private volatile long lastPingSentNanos;

    public ServerRelayService(Logger logger) {
        this.logger = Objects.requireNonNull(logger);
    }

    public synchronized Session start(Config cfg) throws Exception {
        if (session != null) {
            throw new IllegalStateException("VoxelPort relay is already running.");
        }
        if (!starting.compareAndSet(false, true)) {
            throw new IllegalStateException("VoxelPort relay is already starting.");
        }
        try {
            if (cfg.token() == null || cfg.token().isBlank()) {
                throw new IllegalArgumentException("No VoxelPort server token is configured.");
            }

            this.connectionSlots = new java.util.concurrent.Semaphore(cfg.maxConnections());

            CountDownLatch readyLatch = new CountDownLatch(1);
            AtomicLong assignedPort = new AtomicLong(-1);
            java.util.concurrent.atomic.AtomicReference<String> errorRef = new java.util.concurrent.atomic.AtomicReference<>();

            // Clear any stale frames from a previous session BEFORE connecting.
            // onOpen() enqueues the "register" frame while buildAsync() is completing,
            // so clearing here (rather than after .get()) avoids a race that could drop
            // the register — which would leave the relay silent and time us out below.
            sendQueue.clear();

            WebSocket ws = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(cfg.relayWs()), new WebSocket.Listener() {
                        private final StringBuilder textBuffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                            JsonObject register = new JsonObject();
                            register.addProperty("type", "register");
                            register.addProperty("token", cfg.token());
                            if (cfg.blockedIps() != null && !cfg.blockedIps().isEmpty()) {
                                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                                for (String ip : cfg.blockedIps()) arr.add(ip.trim());
                                register.add("blocked_ips", arr);
                            }
                            sendJson(webSocket, register);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            webSocket.request(1);
                            textBuffer.append(data);
                            if (textBuffer.length() > MAX_FRAME_BASE64_CHARS + 4096) {
                                textBuffer.setLength(0);
                                errorRef.compareAndSet(null, "relay frame exceeded maximum size");
                                readyLatch.countDown();
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "frame too large");
                                return null;
                            }
                            if (last) {
                                String raw = textBuffer.toString();
                                textBuffer.setLength(0);
                                handleFrame(webSocket, raw, assignedPort, errorRef, readyLatch);
                            }
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            errorRef.compareAndSet(null, error.getMessage());
                            readyLatch.countDown();
                            handleDisconnect(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            readyLatch.countDown();
                            handleDisconnect(webSocket);
                            return null;
                        }
                    })
                    .get(15, TimeUnit.SECONDS);
            this.config = cfg;
            this.socket = ws;
            senderThread = startSenderThread(ws);

            if (!readyLatch.await(15, TimeUnit.SECONDS)) {
                this.config = null;
                this.socket = null;
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "timeout");
                throw new IOException("Timed out waiting for relay to assign a public port.");
            }

            String error = errorRef.get();
            if (error != null) {
                this.config = null;
                this.socket = null;
                try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "error"); } catch (Exception ignored) {}
                throw new IOException("Relay error: " + error);
            }

            int port = (int) assignedPort.get();
            if (port <= 0 || port > 65535) {
                this.config = null;
                this.socket = null;
                try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "bad port"); } catch (Exception ignored) {}
                throw new IOException("Relay did not assign a valid public port.");
            }

            this.session = new Session(cfg.serverPort(), port, cfg.publicHost() + ":" + port, Instant.now());
            this.relayPingMs.set(-1);
            this.lastPingSentNanos = 0L;

            pingTask = scheduler.scheduleAtFixedRate(() -> {
                WebSocket current = socket;
                if (current == null || current.isOutputClosed()) return;
                try {
                    // Bug-1 fix: record timestamp AFTER a successful send so a failed
                    // send doesn't leave lastPingSentNanos pointing at a stale cycle.
                    sendJson(current, frame("ping"));
                    lastPingSentNanos = System.nanoTime();
                } catch (Exception ignored) {}
            }, 25, 25, TimeUnit.SECONDS);

            logger.info("VoxelPort server relay started. Players connect via {}", session.publicAddress());
            return session;
        } finally {
            starting.set(false);
        }
    }

    public synchronized void stop() {
        Thread st = senderThread; senderThread = null;
        if (st != null) st.interrupt();
        sendQueue.clear();
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
        WebSocket ws = socket;
        socket = null;
        session = null;
        config = null;
        relayPingMs.set(-1);
        closeAllConnections();
        if (ws != null) {
            try { ws.abort(); } catch (Exception ignored) {}
        }
    }

    public boolean isRunning() {
        return session != null;
    }

    public boolean isStarting() {
        return starting.get();
    }

    public Session getSession() {
        return session;
    }

    public int getActiveConnections() {
        return connections.size();
    }

    public long getTotalConnections() {
        return totalConnections.get();
    }

    public long getBytesFromServer() {
        return bytesFromServer.get();
    }

    public long getBytesToServer() {
        return bytesToServer.get();
    }

    public long getRelayPingMs() {
        return relayPingMs.get();
    }

    private void handleFrame(WebSocket ws, String raw,
                             AtomicLong assignedPort,
                             java.util.concurrent.atomic.AtomicReference<String> errorRef,
                             CountDownLatch readyLatch) {
        try {
            JsonObject msg = JsonParser.parseString(raw).getAsJsonObject();
            String type = optString(msg, "type");
            if (type == null) return;

            switch (type) {
                case "port" -> {
                    int port = optInt(msg, "port", -1);
                    if (port > 0) {
                        assignedPort.set(port);
                    } else {
                        errorRef.compareAndSet(null, "relay returned invalid public port");
                    }
                    readyLatch.countDown();
                }
                case "connect" -> {
                    String conn = optString(msg, "conn");
                    String ip = optString(msg, "ip");
                    if (conn != null) openPlayerConnection(conn, ip);
                }
                case "data" -> {
                    String conn = optString(msg, "conn");
                    String data = optString(msg, "data");
                    if (conn != null && data != null) forwardToServer(conn, data);
                }
                case "close" -> {
                    String conn = optString(msg, "conn");
                    if (conn != null) closePlayerConnection(conn, true);
                }
                case "pong" -> {
                    long sent = lastPingSentNanos;
                    if (sent != 0L) relayPingMs.set((System.nanoTime() - sent) / 1_000_000L);
                }
                case "error" -> {
                    String message = optString(msg, "message");
                    errorRef.compareAndSet(null, message != null ? message : "unknown relay error");
                    readyLatch.countDown();
                }
            }
        } catch (Exception e) {
            logger.warn("VoxelPort: Ignoring malformed relay frame: {}", e.getMessage());
        }
    }

    private void handleDisconnect(WebSocket ws) {
        if (socket == ws) {
            stop();
        }
    }

    private void openPlayerConnection(String connId, String realIp) {
        Config cfg = config;
        WebSocket ws = socket;
        if (cfg == null || ws == null) return;
        
        // 1.3: Enforce blocked IPs
        if (realIp != null && !realIp.isEmpty() && cfg.blockedIps().contains(realIp)) {
            logger.warn("VoxelPort: Rejected connection from blocked IP {}", realIp);
            sendClose(connId);
            return;
        }

        // 1.4: Fix max connection race using Semaphore
        java.util.concurrent.Semaphore slots = connectionSlots;
        if (slots == null || !slots.tryAcquire()) {
            sendClose(connId);
            return;
        }

        final PlayerConn[] slotHolder = new PlayerConn[1];
        connections.compute(connId, (id, existing) -> {
            if (existing != null) {
                return existing;
            }
            PlayerConn pc = new PlayerConn();
            slotHolder[0] = pc;
            return pc;
        });

        PlayerConn pc = slotHolder[0];
        if (pc == null) {
            // Already existed (duplicate connId). We didn't add it, so release the permit we just acquired.
            slots.release();
            sendClose(connId);
            return;
        }

        totalConnections.incrementAndGet();

        ioPool.execute(() -> runPlayerConnection(connId, realIp, pc, cfg));
    }

    private void runPlayerConnection(String connId, String realIp, PlayerConn pc, Config cfg) {
        Socket local = new Socket();
        try {
            synchronized (pc) {
                if (pc.closed) {
                    closeQuietly(local);
                    return;
                }
                pc.socket = local;
            }

            local.setTcpNoDelay(true);
            local.connect(new InetSocketAddress(cfg.serverHost(), cfg.serverPort()), LOCAL_CONNECT_TIMEOUT_MS);
            OutputStream out = local.getOutputStream();

            if (cfg.proxyProtocol() && realIp != null && !realIp.isEmpty()) {
                // Validate realIp is a well-formed IP address before embedding it in the
                // PROXY header. A relay-supplied string containing CRLF or other characters
                // would otherwise inject arbitrary bytes into the server's connection stream.
                InetAddress addr;
                try {
                    addr = InetAddress.getByName(realIp);
                } catch (java.net.UnknownHostException e) {
                    logger.warn("VoxelPort: Rejected invalid relay-supplied IP '{}' for PROXY header", realIp);
                    closePlayerConnection(connId, false);
                    sendClose(connId);
                    return;
                }
                // Use the canonical numeric form so no hostname or special characters survive.
                String safeIp = addr.getHostAddress();
                boolean isIpv6 = safeIp.contains(":");
                String proxyHeader = "PROXY " + (isIpv6 ? "TCP6" : "TCP4") + " " + safeIp + " 127.0.0.1 " +
                                     (local.getPort() > 0 ? local.getPort() : 65535) + " " +
                                     cfg.serverPort() + "\r\n";
                out.write(proxyHeader.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.flush();
            }

            while (true) {
                byte[][] buffered;
                synchronized (pc) {
                    if (pc.closed) return;
                    pc.output = out;
                    buffered = pc.pending.toArray(new byte[0][]);
                    pc.pending.clear();
                    pc.pendingBytes = 0;
                    if (buffered.length == 0) {
                        pc.open = true;
                        break;
                    }
                }
                for (byte[] chunk : buffered) {
                    out.write(chunk);
                    bytesToServer.addAndGet(chunk.length);
                }
                out.flush();
            }

            try (InputStream in = local.getInputStream()) {
                byte[] buffer = new byte[READ_BUFFER_BYTES];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    WebSocket ws = socket;
                    if (ws == null || ws.isOutputClosed()) break;
                    bytesFromServer.addAndGet(read);
                    JsonObject frame = frame("data");
                    frame.addProperty("conn", connId);
                    frame.addProperty("data", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, read)));
                    sendJson(ws, frame);
                }
            }
        } catch (Exception ignored) {
            logger.warn("VoxelPort: bridge failed for relay connection {} from {} to {}:{}: {}",
                    connId,
                    realIp != null && !realIp.isBlank() ? realIp : "unknown",
                    cfg.serverHost(),
                    cfg.serverPort(),
                    ignored.getMessage());
        } finally {
            connections.remove(connId, pc);
            teardown(pc, false);
            sendClose(connId);
        }
    }

    private void forwardToServer(String connId, String base64Data) {
        if (base64Data.length() > MAX_FRAME_BASE64_CHARS) {
            closePlayerConnection(connId, false);
            sendClose(connId);
            return;
        }

        PlayerConn pc = connections.get(connId);
        if (pc == null) return;

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            return;
        }

        OutputStream out;
        synchronized (pc) {
            if (pc.closed) return;
            if (!pc.open) {
                if (pc.pendingBytes + decoded.length > MAX_DECODED_FRAME_BYTES) {
                    closePlayerConnection(connId, false);
                    sendClose(connId);
                } else {
                    pc.pending.add(decoded);
                    pc.pendingBytes += decoded.length;
                }
                return;
            }
            out = pc.output;
        }

        if (out == null) {
            closePlayerConnection(connId, false);
            sendClose(connId);
            return;
        }

        try {
            out.write(decoded);
            out.flush();
            bytesToServer.addAndGet(decoded.length);
        } catch (IOException e) {
            closePlayerConnection(connId, false);
            sendClose(connId);
        }
    }

    private void closePlayerConnection(String connId, boolean relayInitiated) {
        PlayerConn pc = connections.remove(connId);
        if (pc != null) {
            teardown(pc, relayInitiated);
            java.util.concurrent.Semaphore slots = connectionSlots;
            if (slots != null) slots.release();
        }
    }

    private void sendClose(String connId) {
        WebSocket ws = socket;
        if (ws == null || ws.isOutputClosed()) return;
        JsonObject frame = frame("close");
        frame.addProperty("conn", connId);
        sendJson(ws, frame);
    }

    private void closeAllConnections() {
        int count = connections.size();
        for (PlayerConn pc : connections.values()) {
            teardown(pc, false);
        }
        connections.clear();
        java.util.concurrent.Semaphore slots = connectionSlots;
        if (slots != null && count > 0) slots.release(count);
    }

    private static void teardown(PlayerConn pc, boolean relayInitiated) {
        synchronized (pc) {
            pc.closed = true;
            pc.open = false;
            pc.pending.clear();
            pc.pendingBytes = 0;
            closeQuietly(pc.socket);
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void sendJson(WebSocket ws, JsonObject obj) {
        if (!sendQueue.offer(obj.toString())) {
            logger.warn("VoxelPort: WebSocket send queue full — dropping frame");
        }
    }

    private Thread startSenderThread(WebSocket ws) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !ws.isOutputClosed()) {
                String msg;
                try {
                    msg = sendQueue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (msg == null) continue;
                try {
                    ws.sendText(msg, true).get(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.warn("VoxelPort: WebSocket send failed: {}", e.getMessage());
                    if (ws.isOutputClosed()) break;
                }
            }
        }, "voxelport-ws-sender");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static JsonObject frame(String type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        return obj;
    }

    private static String optString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : null;
    }

    private static int optInt(JsonObject obj, String key, int fallback) {
        try {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsInt() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static final class PlayerConn {
        Socket socket;
        OutputStream output;
        boolean open;
        boolean closed;
        final ArrayDeque<byte[]> pending = new ArrayDeque<>();
        int pendingBytes;
    }

    public record Config(String relayWs, String token, String publicHost, String serverHost,
                         int serverPort, int maxConnections, boolean proxyProtocol, java.util.List<String> blockedIps) {}

    public static final class Session {
        private final int serverPort;
        private final int publicPort;
        private final String publicAddress;
        private final Instant startedAt;

        Session(int serverPort, int publicPort, String publicAddress, Instant startedAt) {
            this.serverPort = serverPort;
            this.publicPort = publicPort;
            this.publicAddress = publicAddress;
            this.startedAt = startedAt;
        }

        public int serverPort() { return serverPort; }
        public int publicPort() { return publicPort; }
        public String publicAddress() { return publicAddress; }

        public String uptimeLabel() {
            Duration d = Duration.between(startedAt, Instant.now());
            long h = d.toHours(), m = d.toMinutesPart(), s = d.toSecondsPart();
            if (h > 0) return h + "h " + m + "m";
            if (m > 0) return m + "m " + s + "s";
            return s + "s";
        }
    }
}
