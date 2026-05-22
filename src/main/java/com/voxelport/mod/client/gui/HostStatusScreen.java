package com.voxelport.mod.client.gui;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.logic.HostingService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HostStatusScreen extends Screen {
    private final Screen parent;
    private final HostingService.HostSession session;

    public HostStatusScreen(Screen parent, HostingService.HostSession session) {
        super(Component.literal("VoxelPort Host Status"));
        this.parent = parent;
        this.session = session;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Copy Room Code"), button -> {
            this.minecraft.keyboardHandler.setClipboard(session.getCode());
            button.setMessage(Component.literal("§aCopied!"));
        }).bounds(this.width / 2 - 102, 92, 204, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Stop Session"), button -> {
            VoxelPortMod.getHostingService().stop();
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 102, 118, 204, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 102, 146, 204, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gui, mouseX, mouseY, delta);

        gui.text(this.font, this.title,
                this.width / 2 - this.font.width(this.title) / 2, 20, 0xFFFFFFFF, true);

        String statusText = "Status: §aLive and accepting connections";
        gui.text(this.font, statusText,
                this.width / 2 - this.font.width(statusText) / 2, 45, 0xFFFFFFFF, true);

        String uptimeText = "Uptime: " + session.getUptimeLabel();
        gui.text(this.font, uptimeText,
                this.width / 2 - this.font.width(uptimeText) / 2, 59, 0xFFA0A0A0, true);

        int players = VoxelPortMod.getHostingService().getPlayerCount();
        String playersText = "Players connected: " + players;
        gui.text(this.font, playersText,
                this.width / 2 - this.font.width(playersText) / 2, 73, 0xFFA0A0A0, true);

        String codeLabel = "Share this code with your friends:";
        gui.text(this.font, codeLabel,
                this.width / 2 - this.font.width(codeLabel) / 2, 180, 0xFFA0A0A0, true);

        String code = session.getCode();
        gui.text(this.font, code,
                this.width / 2 - this.font.width(code) / 2, 192, 0xFFFFFF55, true);

        gui.text(this.font, VoxelPortMod.versionLabel(), 6, this.height - 12, 0xFF808080, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!VoxelPortMod.getHostingService().isRunning()) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
