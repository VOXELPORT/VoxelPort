package com.voxelport.mod.mixin;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.client.gui.DiscordVerifyScreen;
import com.voxelport.mod.client.gui.HostStatusScreen;
import com.voxelport.mod.logic.DiscordVerifyService;
import com.voxelport.mod.logic.HostingService;
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
        HostingService svc = VoxelPortMod.getHostingService();
        Component label = svc.isRunning()
                ? Component.literal("VoxelPort: Live")
                : Component.literal("Open to VoxelPort");

        this.addRenderableWidget(Button.builder(label, button -> {
            if (svc.isRunning()) {
                this.minecraft.setScreen(new HostStatusScreen(this, svc.getSession()));
            } else {
                IntegratedServer server = this.minecraft.getSingleplayerServer();
                if (server == null) return;
                int port = server.getPort();

                DiscordVerifyService.DiscordProfile cached =
                        VoxelPortMod.getDiscordVerifyService().readCachedProfile();
                if (cached != null && cached.isValid()) {
                    startHostingBackground(svc, port, button);
                } else {
                    this.minecraft.setScreen(new DiscordVerifyScreen(this, profile -> {
                        startHostingBackground(svc, port, button);
                    }));
                }
            }
        }).bounds(this.width / 2 - 102, this.height / 4 + 144 - 16, 204, 20).build());
    }

    private void startHostingBackground(HostingService svc, int port, Button button) {
        new Thread(() -> {
            try {
                DiscordVerifyService.DiscordProfile profile =
                        VoxelPortMod.getDiscordVerifyService().readCachedProfile();
                if (profile != null && !VoxelPortMod.getDiscordVerifyService().validateWithBot(profile.userId())) {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(Component.literal(
                                        "§8[§aVoxelPort§8] §cYour Discord account is no longer authorized."));
                            }
                        });
                    }
                    return;
                }
                HostingService.HostSession session = svc.start(port);
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        this.minecraft.keyboardHandler.setClipboard(session.getCode());
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal(
                                    "§8[§aVoxelPort§8] §aSession started! Room code copied: §b" + session.getCode()));
                        }
                        button.setMessage(Component.literal("VoxelPort: Live"));
                    });
                }
            } catch (Exception e) {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal(
                                    "§8[§aVoxelPort§8] §cFailed to start session: " + e.getMessage()));
                        }
                    });
                }
            }
        }, "voxelport-host").start();
    }
}
