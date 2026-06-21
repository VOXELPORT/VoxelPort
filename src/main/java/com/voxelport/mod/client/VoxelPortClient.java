package com.voxelport.mod.client;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.logic.HostingService;
import com.voxelport.mod.logic.JoinService;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxelPortClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("VoxelPort-Client");
    private static JoinService joinService;

    private static final long TICK_INTERVAL_MS = 2000L;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing VoxelPort Client v{}...", VoxelPortMod.VERSION);
        java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("voxelport");
        joinService = new JoinService(configDir, LOGGER);
        Thread tickThread = new Thread(this::clientTick, "voxelport-client-tick");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    public static JoinService getJoinService() {
        return joinService;
    }

    private void clientTick() {
        while (true) {
            try {
                Thread.sleep(TICK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            Minecraft mc = Minecraft.getInstance();

            // Deliver any pending system notices
            String notice = VoxelPortMod.consumePendingUserNotice();
            if (notice != null) {
                if (mc.player != null) {
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§aVoxelPort§8] §e" + notice));
                        }
                    });
                } else {
                    VoxelPortMod.setPendingUserNotice(notice);
                }
            }

            // Update tab list header with live relay stats
            if (mc.player != null && mc.player.connection != null) {
                mc.execute(() -> updateTabList(mc));
            }
        }
    }

    private static void updateTabList(Minecraft mc) {
        if (mc.player == null || mc.player.connection == null) return;

        HostingService hostSvc = VoxelPortMod.getHostingService();
        JoinService joinSvc = joinService;

        Component header = null;
        Component footer = Component.literal("§7discord.com/invite/5Q6BRnJYHW  §8|  §7voxelport.in");

        if (hostSvc != null && hostSvc.isRunning()) {
            HostingService.HostSession session = hostSvc.getSession();
            long ping = hostSvc.getRelayPingMs();
            int players = hostSvc.getPlayerCount();
            String pingStr = ping >= 0 ? ping + "ms" : "…";
            String pingColor = ping < 0 ? "§7" : ping < 80 ? "§a" : ping < 150 ? "§e" : "§c";
            header = Component.literal(
                "§a§lVoxelPort§r  §8|  §7Room: §b" + session.getCode()
                + "  §8|  §7Relay: " + pingColor + pingStr
                + "  §8|  §7Players: §b" + players);
        } else if (joinSvc != null && joinSvc.isRunning()) {
            long ping = joinSvc.getRelayPingMs();
            String pingStr = ping >= 0 ? ping + "ms" : "…";
            String pingColor = ping < 0 ? "§7" : ping < 80 ? "§a" : ping < 150 ? "§e" : "§c";
            header = Component.literal(
                "§a§lVoxelPort§r  §8|  §7Relay: " + pingColor + pingStr);
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
