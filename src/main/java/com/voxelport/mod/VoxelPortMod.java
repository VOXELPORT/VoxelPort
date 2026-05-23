package com.voxelport.mod;

import com.voxelport.mod.logic.AutoUpdater;
import com.voxelport.mod.logic.DiscordVerifyService;
import com.voxelport.mod.logic.HostingService;
import com.voxelport.mod.logic.VoxelPortConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class VoxelPortMod implements ModInitializer {
    public static final String MOD_ID = "voxelport";
    public static final String WEBSITE = "https://github.com/voxelport";
    public static final String DISCORD = "https://discord.gg/EuDMWUuGpp";
    public static final String VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    public static final Logger LOGGER = LoggerFactory.getLogger("VoxelPort");

    private static HostingService hostingService;
    private static DiscordVerifyService discordVerifyService;
    private static VoxelPortConfig config;
    private static final java.util.concurrent.atomic.AtomicReference<String> pendingUserNotice =
            new java.util.concurrent.atomic.AtomicReference<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing VoxelPort v{} ({}, {})...", VERSION, WEBSITE, DISCORD);
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

        // Load config first so relay URL override is set before any service uses it
        config = new VoxelPortConfig(configDir, LOGGER);
        config.load();

        hostingService = new HostingService(configDir, LOGGER);
        discordVerifyService = new DiscordVerifyService(configDir, LOGGER);
        AutoUpdater.checkAsync(VERSION, LOGGER, pendingUserNotice::set);
    }

    public static HostingService getHostingService() { return hostingService; }
    public static DiscordVerifyService getDiscordVerifyService() { return discordVerifyService; }
    public static VoxelPortConfig getConfig() { return config; }

    public static String consumePendingUserNotice() { return pendingUserNotice.getAndSet(null); }
    public static void setPendingUserNotice(String notice) { pendingUserNotice.set(notice); }
    public static String versionLabel() { return "VoxelPort v" + VERSION; }
}
