package com.voxelport.mod.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.voxelport.mod.VoxelPortMod;
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
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("voxelport")
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

        dispatcher.register(root);
    }

    private static int start(CommandSourceStack source, int port) {
        ServerRelayService service = VoxelPortMod.getServerRelayService();
        VoxelPortConfig config = VoxelPortMod.getConfig();
        if (service == null || config == null) {
            source.sendFailure(Component.literal("VoxelPort is not initialized yet."));
            return 0;
        }
        if (service.isRunning()) {
            source.sendFailure(Component.literal("VoxelPort is already live. Address: "
                    + service.getSession().publicAddress()));
            return 0;
        }

        String token = config.getServerToken();
        if (token == null || token.isBlank()) {
            source.sendFailure(Component.literal(
                    "No VoxelPort token set. Run /voxelport token <token> first."));
            return 0;
        }

        ServerRelayService.Config relayConfig = new ServerRelayService.Config(
                RelayUrlResolver.get(),
                token,
                config.getPublicHost(),
                config.getServerHost(),
                port,
                config.getMaxConnections());

        source.sendSuccess(() -> Component.literal("Connecting to VoxelPort relay on local port " + port + "..."), false);
        Thread thread = new Thread(() -> {
            try {
                ServerRelayService.Session session = service.start(relayConfig);
                source.getServer().execute(() -> source.sendSuccess(() -> Component.literal(
                        "VoxelPort is live. Players connect via: " + session.publicAddress()), true));
            } catch (Exception e) {
                source.getServer().execute(() -> source.sendFailure(Component.literal(
                        "Failed to start VoxelPort: " + e.getMessage())));
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
        config.setServerToken(token);
        config.save();
        source.sendSuccess(() -> Component.literal("VoxelPort token saved. Run /voxelport start to connect."), false);
        return 1;
    }

    private static int defaultServerPort(MinecraftServer server) {
        if (server instanceof DedicatedServer dedicatedServer) {
            return dedicatedServer.getServerPort();
        }
        return server.getPort();
    }
}
