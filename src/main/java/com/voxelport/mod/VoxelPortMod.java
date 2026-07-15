package com.voxelport.mod;

import com.voxelport.mod.logic.VoxelPortConfig;
import com.voxelport.mod.server.ServerRelayService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class VoxelPortMod implements ModInitializer {
    public static final String MOD_ID = "voxelport";
    public static final String WEBSITE = "https://www.voxelport.in";
    public static final String VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    public static final Logger LOGGER = LoggerFactory.getLogger("VoxelPort");

    private static ServerRelayService serverRelayService;
    private static VoxelPortConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing VoxelPort v{} ({})...", VERSION, WEBSITE);
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

        // Load config first so relay URL override is set before any service uses it
        config = VoxelPortConfig.create(configDir, LOGGER);
        config.load();
        // No Discord required — mint a local device token on first run if absent.
        config.ensureDeviceToken();

        serverRelayService = new ServerRelayService(LOGGER);
    }

    public static ServerRelayService getServerRelayService() { return serverRelayService; }
    public static VoxelPortConfig getConfig() { return config; }

    public static String versionLabel() { return "VoxelPort v" + VERSION; }
}
