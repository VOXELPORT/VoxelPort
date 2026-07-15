package com.voxelport.mod.client.gui;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.logic.RelayUrlResolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SettingsScreen extends Screen {
    private final Screen parent;

    private EditBox relayField;
    private EditBox tokenField;
    private Button testButton;
    private String statusMessage = "";
    private boolean testing = false;

    public SettingsScreen(Screen parent) {
        super(Component.literal("VoxelPort Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        relayField = new EditBox(this.font, cx - 150, 66, 300, 20,
                Component.literal("Relay URL"));
        relayField.setMaxLength(256);
        relayField.setHint(Component.literal("wss://relay.voxelport.in"));
        // Pre-fill with currently saved custom URL (blank = using default)
        String saved = VoxelPortMod.getConfig().getRelayUrl();
        if (saved != null) relayField.setValue(saved);
        this.addWidget(relayField);

        tokenField = new EditBox(this.font, cx - 150, 96, 300, 20,
                Component.literal("Device Token"));
        tokenField.setMaxLength(256);
        // Tokens are generated automatically on first run — no Discord, no signup.
        // This field is an advanced override; leave blank to keep the auto token.
        tokenField.setHint(Component.literal("Auto-generated — leave blank (advanced override)"));
        this.addWidget(tokenField);

        // Relay override controls: test reachability, or reset to the default relay.
        testButton = this.addRenderableWidget(
                Button.builder(Component.literal("Test"), button -> testConnection())
                        .bounds(cx - 150, 126, 144, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("↩ Default"), button -> {
                    relayField.setValue("");
                    statusMessage = "§7Reset to default relay.";
                })
                        .bounds(cx + 6, 126, 144, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("Save & Close"), button -> saveAndClose())
                        .bounds(cx - 150, 154, 144, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), button -> this.minecraft.setScreen(parent))
                        .bounds(cx + 6, 154, 144, 20)
                        .build());
    }

    private void saveAndClose() {
        String rawUrl = relayField.getValue().trim();
        String url = RelayUrlResolver.normalizeOfficialRelayUrl(rawUrl);
        if (!url.isBlank() && !url.startsWith("wss://")) {
            statusMessage = "§cURL must start with wss://";
            return;
        }
        if (!rawUrl.isBlank() && RelayUrlResolver.hasInvalidOfficialPort(rawUrl)) {
            statusMessage = "§cUse wss://relay.voxelport.in. Player ports are for joining.";
            return;
        }
        String token = tokenField.getValue().trim();
        if (!token.isBlank() && !token.matches("^vp_[A-Za-z0-9_-]+$")) {
            statusMessage = "§cToken must start with vp_.";
            return;
        }

        String oldUrl = RelayUrlResolver.get();
        VoxelPortMod.getConfig().setRelayUrl(url.isBlank() ? null : url);
        if (!token.isBlank()) {
            VoxelPortMod.getConfig().setServerToken(token);
        }
        VoxelPortMod.getConfig().save();
        String newUrl = RelayUrlResolver.get();

        // If the relay is running and the URL changed, restart it automatically.
        // The universal token works on all regions — no need for the user to do anything else.
        com.voxelport.mod.server.ServerRelayService service = VoxelPortMod.getServerRelayService();
        if (service != null && service.isRunning() && !newUrl.equals(oldUrl)) {
            int port = service.getSession().serverPort();
            service.stop();
            com.voxelport.mod.logic.VoxelPortConfig cfg = VoxelPortMod.getConfig();
            com.voxelport.mod.server.ServerRelayService.Config relayCfg =
                    new com.voxelport.mod.server.ServerRelayService.Config(
                            newUrl, cfg.getServerToken(),
                            cfg.getPublicHost(), cfg.getServerHost(),
                            port, cfg.getMaxConnections(),
                            cfg.isProxyProtocol(), cfg.getBlockedIps());
            new Thread(() -> {
                try {
                    service.start(relayCfg);
                } catch (Exception e) {
                    VoxelPortMod.LOGGER.warn("VoxelPort: failed to restart relay on region switch", e);
                }
            }, "voxelport-region-switch").start();
        }

        this.minecraft.setScreen(parent);
    }

    private void testConnection() {
        if (testing) return;
        String raw = relayField.getValue().trim();
        String url = raw.isBlank() ? RelayUrlResolver.get() : RelayUrlResolver.normalizeOfficialRelayUrl(raw);

        if (!url.startsWith("wss://")) {
            statusMessage = "§cURL must start with wss://";
            return;
        }
        if (!raw.isBlank() && RelayUrlResolver.hasInvalidOfficialPort(raw)) {
            statusMessage = "§cUse wss://relay.voxelport.in. Player ports are for joining.";
            return;
        }

        testing = true;
        testButton.active = false;
        statusMessage = "§7Connecting to " + url + "…";

        new Thread(() -> {
            try {
                URI uri = URI.create(url);
                long start = System.currentTimeMillis();
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> error = new AtomicReference<>();

                HttpClient testClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                testClient.newWebSocketBuilder()
                        .buildAsync(uri, new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket ws) {
                                latch.countDown();
                                ws.sendClose(WebSocket.NORMAL_CLOSURE, "test");
                            }
                            @Override
                            public void onError(WebSocket ws, Throwable t) {
                                error.set(t.getMessage());
                                latch.countDown();
                            }
                            @Override
                            public java.util.concurrent.CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                                latch.countDown();
                                return null;
                            }
                        })
                        .exceptionally(t -> {
                            error.set(t.getCause() != null ? t.getCause().getMessage() : t.getMessage());
                            latch.countDown();
                            return null;
                        });

                boolean completed = latch.await(10, TimeUnit.SECONDS);
                long ms = System.currentTimeMillis() - start;
                String err = error.get();

                this.minecraft.execute(() -> {
                    testing = false;
                    testButton.active = true;
                    if (!completed) {
                        statusMessage = "§cTimed out after 10 seconds.";
                    } else if (err != null) {
                        statusMessage = "§cFailed: " + err;
                    } else {
                        statusMessage = "§aConnected in " + ms + "ms — relay is reachable!";
                    }
                });
            } catch (Exception e) {
                this.minecraft.execute(() -> {
                    testing = false;
                    testButton.active = true;
                    statusMessage = "§cError: " + e.getMessage();
                });
            }
        }, "voxelport-test").start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        super.extractRenderState(gui, mouseX, mouseY, delta);
        int cx = this.width / 2;

        gui.text(this.font, this.title,
                cx - this.font.width(this.title) / 2, 20, 0xFFFFFFFF, true);

        String sub = "Customize which relay server the mod connects to.";
        gui.text(this.font, sub, cx - this.font.width(sub) / 2, 36, 0xFFA0A0A0, true);

        gui.text(this.font, "Relay Server URL", cx - 150, 54, 0xFFE0E0E0, true);
        gui.text(this.font, "Device Token (auto)", cx - 150, 84, 0xFFE0E0E0, true);

        String hint = "Players join the copied public address in vanilla Multiplayer.";
        gui.text(this.font, hint, cx - 150, 188, 0xFF606060, true);

        relayField.extractWidgetRenderState(gui, mouseX, mouseY, delta);
        tokenField.extractWidgetRenderState(gui, mouseX, mouseY, delta);

        if (!statusMessage.isEmpty()) {
            gui.text(this.font, statusMessage,
                    cx - this.font.width(statusMessage) / 2, 206, 0xFFFFFF55, true);
        }

        String current = "Active: " + RelayUrlResolver.get();
        gui.text(this.font, current, cx - this.font.width(current) / 2,
                this.height - 24, 0xFF505050, true);

        gui.text(this.font, VoxelPortMod.versionLabel(), 6, this.height - 12, 0xFF808080, true);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) { // Enter
            saveAndClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
