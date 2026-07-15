package com.voxelport.mod.mixin;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.client.gui.HostStatusScreen;
import com.voxelport.mod.client.gui.SettingsScreen;
import com.voxelport.mod.logic.RelayErrorMessages;
import com.voxelport.mod.logic.RelayUrlResolver;
import com.voxelport.mod.logic.VoxelPortConfig;
import com.voxelport.mod.server.ServerRelayService;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseMenuMixin extends Screen {
    protected PauseMenuMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        ServerRelayService svc = VoxelPortMod.getServerRelayService();
        if (svc == null) return; // B1 fix: guard — service may not be ready yet
        Component label = svc.isRunning()
                ? Component.literal("VoxelPort: Live")
                : svc.isStarting()
                        ? Component.literal("VoxelPort: Starting...")
                        : Component.literal("Open to VoxelPort");

        Button hostButton = Button.builder(label, button -> {
            if (svc.isRunning()) {
                this.minecraft.setScreen(new HostStatusScreen(this, svc.getSession()));
            } else if (svc.isStarting()) {
                button.active = false;
                button.setMessage(Component.literal("VoxelPort: Starting..."));
                if (this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(Component.literal(
                            "§8[§aVoxelPort§8] §eRelay is already starting."));
                }
            } else {
                VoxelPortConfig config = VoxelPortMod.getConfig();
                if (config == null || config.getServerToken() == null || config.getServerToken().isBlank()) {
                    this.minecraft.setScreen(new SettingsScreen(this));
                    return;
                }
                IntegratedServer server = this.minecraft.getSingleplayerServer();
                if (server == null) return;
                int port = server.getPort();
                if (port <= 0) {
                    if (this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal(
                                "§8[§aVoxelPort§8] §cOpen this world to LAN first, then start VoxelPort."));
                    }
                    return;
                }

                startHostingBackground(svc, port, button);
            }
        }).bounds(this.width / 2 - 102, this.height / 4 + 144 - 16, 154, 20).build();
        hostButton.active = !svc.isStarting();
        this.addRenderableWidget(hostButton);

        this.addRenderableWidget(Button.builder(Component.literal("Settings"), button ->
                this.minecraft.setScreen(new SettingsScreen(this)))
                .bounds(this.width / 2 + 56, this.height / 4 + 144 - 16, 46, 20)
                .build());
    }

    private void startHostingBackground(ServerRelayService svc, int port, Button button) {
        button.active = false;
        button.setMessage(Component.literal("VoxelPort: Starting..."));
        new Thread(() -> {
            try {
                VoxelPortConfig config = VoxelPortMod.getConfig();
                String token = config.getServerToken();
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("No VoxelPort token set. Open VoxelPort settings or run /voxel token <token> first.");
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
                ServerRelayService.Session session = svc.start(relayConfig);
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        this.minecraft.keyboardHandler.setClipboard(session.publicAddress());
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal(
                                    "§8[§aVoxelPort§8] §aRelay started! Address copied: §b" + session.publicAddress()));
                        }
                        button.setMessage(Component.literal("VoxelPort: Live"));
                        button.active = true;
                    });
                }
            } catch (Exception e) {
                VoxelPortMod.LOGGER.warn("VoxelPort: Failed to start relay session", e);
                if (this.minecraft != null) {
                    String errorMessage = RelayErrorMessages.startFailure(e);
                    this.minecraft.execute(() -> {
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal(
                                    "§8[§aVoxelPort§8] §c" + errorMessage));
                        }
                        button.setMessage(Component.literal("Open to VoxelPort"));
                        button.active = true;
                    });
                }
            }
        }, "voxelport-host").start();
    }
}
