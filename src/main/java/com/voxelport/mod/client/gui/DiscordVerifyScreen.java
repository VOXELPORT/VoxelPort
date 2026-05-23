package com.voxelport.mod.client.gui;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.logic.DiscordVerifyService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class DiscordVerifyScreen extends Screen {
    private enum Step { USERNAME, CODE }

    private final Screen parent;
    private final Consumer<DiscordVerifyService.DiscordProfile> onVerified;

    private Step step = Step.USERNAME;
    private EditBox inputField;
    private Button actionButton;
    private String statusMessage = "Enter your Discord username to receive a verification code via DM.";
    private boolean working = false;

    public DiscordVerifyScreen(Screen parent, Consumer<DiscordVerifyService.DiscordProfile> onVerified) {
        super(Component.literal("VoxelPort Discord Verification"));
        this.parent = parent;
        this.onVerified = onVerified;
    }

    @Override
    protected void init() {
        if (step == Step.USERNAME) {
            inputField = new EditBox(this.font, this.width / 2 - 100, 70, 200, 20,
                    Component.literal("Discord Username"));
            inputField.setMaxLength(64);
            inputField.setHint(Component.literal("your_username"));
            this.addWidget(inputField);
            this.setInitialFocus(inputField);

            actionButton = this.addRenderableWidget(
                    Button.builder(Component.literal("Send Code via DM"), button -> sendCode())
                            .bounds(this.width / 2 - 100, 100, 200, 20)
                            .build());
        } else {
            inputField = new EditBox(this.font, this.width / 2 - 100, 70, 200, 20,
                    Component.literal("6-Digit Code"));
            inputField.setMaxLength(6);
            inputField.setHint(Component.literal("000000"));
            this.addWidget(inputField);
            this.setInitialFocus(inputField);

            actionButton = this.addRenderableWidget(
                    Button.builder(Component.literal("Verify & Continue"), button -> confirmCode())
                            .bounds(this.width / 2 - 100, 100, 200, 20)
                            .build());
        }

        this.addRenderableWidget(
                Button.builder(Component.literal(step == Step.CODE ? "← Use different username" : "Cancel"),
                        button -> {
                            if (step == Step.CODE) {
                                step = Step.USERNAME;
                                statusMessage = "Enter your Discord username to receive a verification code via DM.";
                                rebuildWidgets();
                            } else {
                                this.minecraft.setScreen(parent);
                            }
                        })
                        .bounds(this.width / 2 - 100, 128, 200, 20)
                        .build());
    }

    private void sendCode() {
        if (working) return;
        String username = inputField.getValue().trim();
        if (username.isEmpty()) {
            statusMessage = "§cPlease enter your Discord username.";
            return;
        }
        working = true;
        actionButton.active = false;
        statusMessage = "Sending verification code...";

        new Thread(() -> {
            try {
                VoxelPortMod.getDiscordVerifyService().startVerification(username);
                this.minecraft.execute(() -> {
                    statusMessage = "§aCode sent! Check your Discord DMs for a 6-digit code.";
                    step = Step.CODE;
                    working = false;
                    rebuildWidgets();
                });
            } catch (Exception e) {
                this.minecraft.execute(() -> {
                    statusMessage = "§cFailed: " + e.getMessage();
                    working = false;
                    if (actionButton != null) actionButton.active = true;
                });
            }
        }, "voxelport-verify-start").start();
    }

    private void confirmCode() {
        if (working) return;
        String code = inputField.getValue().trim();
        if (code.length() != 6) {
            statusMessage = "§cEnter the 6-digit code from your Discord DM.";
            return;
        }
        working = true;
        actionButton.active = false;
        statusMessage = "Verifying...";

        new Thread(() -> {
            try {
                DiscordVerifyService.DiscordProfile profile =
                        VoxelPortMod.getDiscordVerifyService().confirmVerification(code);
                this.minecraft.execute(() -> {
                    this.minecraft.setScreen(parent);
                    onVerified.accept(profile);
                });
            } catch (Exception e) {
                this.minecraft.execute(() -> {
                    statusMessage = "§cVerification failed: " + e.getMessage();
                    inputField.setValue("");
                    working = false;
                    if (actionButton != null) actionButton.active = true;
                });
            }
        }, "voxelport-verify-confirm").start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gui, mouseX, mouseY, delta);

        gui.text(this.font, this.title,
                this.width / 2 - this.font.width(this.title) / 2, 20, 0xFFFFFFFF, true);

        String subtitle = step == Step.USERNAME
                ? "Required to use VoxelPort. We never store your password."
                : "Check your Discord DMs for a 6-digit code.";
        gui.text(this.font, subtitle,
                this.width / 2 - this.font.width(subtitle) / 2, 38, 0xFFA0A0A0, true);

        String fieldLabel = step == Step.USERNAME ? "Discord Username" : "6-Digit Code";
        gui.text(this.font, fieldLabel, this.width / 2 - 100, 57, 0xFFE0E0E0, true);

        if (inputField != null) {
            inputField.extractWidgetRenderState(gui, mouseX, mouseY, delta);
        }

        if (!statusMessage.isEmpty()) {
            gui.text(this.font, statusMessage,
                    this.width / 2 - this.font.width(statusMessage) / 2, 158, 0xFFFFFF55, true);
        }

        String notInServer = "Not in VoxelPort Discord yet? Join discord.gg/EuDMWUuGpp first.";
        gui.text(this.font, notInServer,
                this.width / 2 - this.font.width(notInServer) / 2, this.height - 24, 0xFF606060, true);

        gui.text(this.font, VoxelPortMod.versionLabel(), 6, this.height - 12, 0xFF808080, true);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && !working) {
            if (step == Step.USERNAME) sendCode();
            else confirmCode();
            return true;
        }
        return super.keyPressed(event);
    }
}
