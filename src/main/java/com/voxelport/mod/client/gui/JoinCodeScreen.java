package com.voxelport.mod.client.gui;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.client.VoxelPortClient;
import com.voxelport.mod.logic.DiscordVerifyService;
import com.voxelport.mod.logic.VoxelPortCodec;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class JoinCodeScreen extends Screen {
    private final Screen parent;
    private EditBox codeField;
    private Button joinButton;
    private String errorMessage = "";

    public JoinCodeScreen(Screen parent) {
        super(Component.literal("Join via VoxelPort"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.codeField = new EditBox(this.font, this.width / 2 - 100, 66, 200, 20,
                Component.literal("Room Code"));
        this.codeField.setMaxLength(6);
        this.codeField.setHint(Component.literal("XXXXXX"));
        this.addWidget(this.codeField);

        this.joinButton = this.addRenderableWidget(
                Button.builder(Component.literal("Connect to VoxelPort"), button -> this.onJoin())
                        .bounds(this.width / 2 - 100, 100, 200, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), button -> this.minecraft.setScreen(this.parent))
                        .bounds(this.width / 2 - 100, 124, 200, 20)
                        .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gui, mouseX, mouseY, delta);

        gui.text(this.font, this.title,
                this.width / 2 - this.font.width(this.title) / 2, 20, 0xFFFFFFFF, true);

        String subtitle = "Paste a VoxelPort room code to join a hosted world.";
        gui.text(this.font, subtitle,
                this.width / 2 - this.font.width(subtitle) / 2, 38, 0xFFA0A0A0, true);

        gui.text(this.font, "Room Code (6 characters)",
                this.width / 2 - 100, 53, 0xFFE0E0E0, true);

        this.codeField.extractWidgetRenderState(gui, mouseX, mouseY, delta);

        if (!errorMessage.isEmpty()) {
            gui.text(this.font, errorMessage,
                    this.width / 2 - this.font.width(errorMessage) / 2, 150, 0xFFFF5555, true);
        }

        gui.text(this.font, VoxelPortMod.versionLabel(), 6, this.height - 12, 0xFF808080, true);
    }

    private void onJoin() {
        if (joinButton != null && !joinButton.active) return;

        String rawCode = codeField.getValue().trim();
        if (rawCode.isEmpty()) {
            errorMessage = "Enter a room code.";
            return;
        }

        if (!VoxelPortCodec.isValidCode(rawCode)) {
            errorMessage = "§cInvalid code. Must be 6 alphanumeric characters (e.g. AB12CD).";
            return;
        }

        String code = rawCode.toUpperCase();

        startConnect(code);
    }

    private void startConnect(String code) {
        if (joinButton != null) joinButton.active = false;
        errorMessage = "Connecting to relay...";

        new Thread(() -> {
            try {
                DiscordVerifyService.DiscordProfile profile =
                        VoxelPortMod.getDiscordVerifyService().readCachedProfile();
                if (profile != null && !VoxelPortMod.getDiscordVerifyService().validateWithBot(profile.userId())) {
                    throw new Exception("Your Discord account is no longer authorized.");
                }
                int port = VoxelPortClient.getJoinService().startProxy(code);
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        ServerAddress address = new ServerAddress("127.0.0.1", port);
                        ServerData serverData = new ServerData("VoxelPort Session",
                                "127.0.0.1:" + port, ServerData.Type.OTHER);
                        ConnectScreen.startConnecting(this.parent, this.minecraft, address,
                                serverData, true, new TransferState(Map.of(), Map.of(), true));
                    });
                }
            } catch (Exception e) {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        errorMessage = "§cError: " + e.getMessage();
                        if (joinButton != null) joinButton.active = true;
                    });
                }
            }
        }, "voxelport-join").start();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) {
            onJoin();
            return true;
        }
        return super.keyPressed(event);
    }
}
