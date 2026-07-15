package com.voxelport.mod.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.logic.RelayErrorMessages;
import com.voxelport.mod.logic.RelayUrlResolver;
import com.voxelport.mod.logic.VoxelPortConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

public final class VoxelPortServerCommands {
    private VoxelPortServerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(commandRoot("voxelport"));
        dispatcher.register(commandRoot("voxel"));
        dispatcher.register(commandRoot("vp"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commandRoot(String name) {
        return Commands.literal(name)
                .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), defaultServerPort(ctx.getSource().getServer())))
                        .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "port")))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("address")
                        .executes(ctx -> address(ctx.getSource())))
                .then(Commands.literal("code")
                        .executes(ctx -> address(ctx.getSource())))
                .then(Commands.literal("token")
                        .requires(source -> Commands.LEVEL_OWNERS.check(source.permissions()))
                        .then(Commands.argument("token", StringArgumentType.word())
                                .executes(ctx -> token(ctx.getSource(), StringArgumentType.getString(ctx, "token")))));
    }

    private static int start(CommandSourceStack source, int port) {
        ServerRelayService service = VoxelPortMod.getServerRelayService();
        VoxelPortConfig config = VoxelPortMod.getConfig();
        if (service == null || config == null) {
            source.sendFailure(Component.literal("VoxelPort is not initialized yet."));
            return 0;
        }
        if (port <= 0 || port > 65535) {
            source.sendFailure(Component.literal("No local Minecraft port is open. Open the world to LAN first, or run /voxel start <port>."));
            return 0;
        }
        if (service.isRunning()) {
            source.sendFailure(Component.literal("VoxelPort is already live. Address: "
                    + service.getSession().publicAddress()));
            return 0;
        }
        if (service.isStarting()) {
            source.sendFailure(Component.literal("VoxelPort is already starting."));
            return 0;
        }

        String token = config.getServerToken();
        if (token == null || token.isBlank()) {
            source.sendFailure(Component.literal(
                    "No VoxelPort token set. Run /voxel token <token> first."));
            return 0;
        }

        ServerRelayService.Config relayConfig = new ServerRelayService.Config(
                RelayUrlResolver.get(),
                token,
                config.getPublicHost(),
                config.getServerHost(),
                port,
                config.getMaxConnections(),
                config.isProxyProtocol(),
                config.getBlockedIps());

        source.sendSuccess(() -> Component.literal("Connecting to VoxelPort relay on local port " + port + "..."), false);
        Thread thread = new Thread(() -> {
            try {
                ServerRelayService.Session session = service.start(relayConfig);
                source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(
                        "VoxelPort is live. Players connect via: " + session.publicAddress()), true));
            } catch (Exception e) {
                VoxelPortMod.LOGGER.warn("VoxelPort: Failed to start relay session", e);
                String errorMessage = RelayErrorMessages.startFailure(e);
                source.getServer().execute(() -> source.sendFailure(Component.literal(
                        errorMessage)));
            }
        }, "voxelport-server-start");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ServerRelayService service = VoxelPortMod.getServerRelayService();
        if (service == null || !service.isRunning()) {
            source.sendFailure(Component.literal("VoxelPort is not running."));
            return 0;
        }
        service.stop();
        source.sendSuccess(() -> Component.literal("VoxelPort relay stopped."), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerRelayService service = VoxelPortMod.getServerRelayService();
        if (service == null || !service.isRunning()) {
            source.sendSuccess(() -> Component.literal("VoxelPort is offline."), false);
            return 1;
        }

        ServerRelayService.Session session = service.getSession();
        long ping = service.getRelayPingMs();
        String pingLabel = ping >= 0 ? ping + "ms" : "measuring";
        source.sendSuccess(() -> Component.literal(
                "VoxelPort live. Address: " + session.publicAddress()
                        + ", local port: " + session.serverPort()
                        + ", uptime: " + session.uptimeLabel()
                        + ", active players: " + service.getActiveConnections()
                        + ", total joins: " + service.getTotalConnections()
                        + ", relay ping: " + pingLabel), false);
        return 1;
    }

    private static int address(CommandSourceStack source) {
        ServerRelayService service = VoxelPortMod.getServerRelayService();
        if (service == null || !service.isRunning()) {
            source.sendFailure(Component.literal("VoxelPort is not running."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("VoxelPort address: "
                + service.getSession().publicAddress()), false);
        return 1;
    }

    private static int token(CommandSourceStack source, String token) {
        VoxelPortConfig config = VoxelPortMod.getConfig();
        if (config == null) {
            source.sendFailure(Component.literal("VoxelPort is not initialized yet."));
            return 0;
        }
        
        // SECURITY FIX: Validate token format before storing
        // VoxelPort tokens should start with "vp_" and contain alphanumeric/dash/underscore
        if (!isValidTokenFormat(token)) {
            source.sendFailure(Component.literal(
                    "Invalid token format. VoxelPort tokens start with 'vp_' and contain only letters, numbers, dashes, and underscores."));
            return 0;
        }
        
        config.setServerToken(token);
        config.save();
        
        // SECURITY FIX: Don't echo the token back or show confirmation with it
        // This prevents token exposure in logs
        source.sendSuccess(() -> Component.literal(
                "VoxelPort token saved securely. Run /voxel start to connect."), false);
        return 1;
    }
    
    private static boolean isValidTokenFormat(String token) {
        if (token == null || token.isEmpty()) return false;
        return token.matches("^vp_[A-Za-z0-9_-]+$");
    }

    private static int defaultServerPort(MinecraftServer server) {
        if (server instanceof DedicatedServer dedicatedServer) {
            return dedicatedServer.getServerPort();
        }
        return server.getPort();
    }
}
