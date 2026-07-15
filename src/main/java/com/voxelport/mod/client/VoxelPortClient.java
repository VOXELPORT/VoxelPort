package com.voxelport.mod.client;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.server.ServerRelayService;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoxelPortClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("VoxelPort-Client");

    private static final long TICK_INTERVAL_MS = 2000L;

    // B5 fix: use ExecutorService for background thread lifecycle
    private volatile ExecutorService tickExecutor;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing VoxelPort Client v{}...", VoxelPortMod.VERSION);
        startTickThread();
    }

    /** Starts the background tick thread. Guards against double-starts. */
    private synchronized void startTickThread() {
        if (tickExecutor != null && !tickExecutor.isShutdown()) {
            // Already running — interrupt the old one before spawning a new one
            tickExecutor.shutdownNow();
        }
        tickExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "voxelport-client-tick");
            t.setDaemon(true);
            return t;
        });
        tickExecutor.submit(this::clientTick);
    }

    /** Stops the background tick thread. Safe to call from any thread. */
    public synchronized void stopTickThread() {
        if (tickExecutor != null) {
            tickExecutor.shutdownNow();
            tickExecutor = null;
        }
    }

    private void clientTick() { 
        while (!Thread.currentThread().isInterrupted()) {
            try {
                try {
                    Thread.sleep(TICK_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc == null) continue; // not yet initialized

                // Update tab list header with live relay stats
                var player = mc.player;
                if (player != null && player.connection != null) {
                    mc.execute(() -> updateTabList(mc));
                }
            } catch (Exception e) {
                // 3.1: Harden clientTick loop against unexpected exceptions
                LOGGER.warn("Exception in client tick loop: {}", e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private static void updateTabList(Minecraft mc) {
        var player = mc.player;
        if (player == null || player.connection == null) return;

        ServerRelayService relay = VoxelPortMod.getServerRelayService();

        Component header = null;
        Component footer = Component.literal("§7voxelport.in  §8|  §7Host without port forwarding");

        if (relay != null && relay.isRunning()) {
            ServerRelayService.Session session = relay.getSession();
            long ping = relay.getRelayPingMs();
            int players = relay.getActiveConnections();
            String pingStr = ping >= 0 ? ping + "ms" : "…";
            String pingColor = ping < 0 ? "§7" : ping < 80 ? "§a" : ping < 150 ? "§e" : "§c";
            header = Component.literal(
                "§a§lVoxelPort§r  §8|  §7Address: §b" + session.publicAddress()
                + "  §8|  §7Relay: " + pingColor + pingStr
                + "  §8|  §7Players: §b" + players);
        }

        if (header != null) {
            try {
                PlayerTabOverlay tabList = mc.gui.getTabList();
                tabList.setHeader(header);
                tabList.setFooter(footer);
            } catch (Exception ignored) {}
        }
    }
}
