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
        relayField.setHint(Component.literal("wss://voxelportrelay.qzz.io/relay"));
        // Pre-fill with currently saved custom URL (blank = using default)
        String saved = VoxelPortMod.getConfig().getRelayUrl();
        if (saved != null) relayField.setValue(saved);
        this.addWidget(relayField);

        testButton = this.addRenderableWidget(
                Button.builder(Component.literal("Test Connection"), button -> testConnection())
                        .bounds(cx - 150, 96, 144, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("Reset to Default"), button -> {
                    relayField.setValue("");
                    statusMessage = "§7Reset to default relay.";
                })
                        .bounds(cx + 6, 96, 144, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("Save & Close"), button -> saveAndClose())
                        .bounds(cx - 150, 124, 144, 20)
                        .build());

        this.addRenderableWidget(
                Button.builder(Component.literal("Cancel"), button -> this.minecraft.setScreen(parent))
                        .bounds(cx + 6, 124, 144, 20)
                        .build());
    }

    private void saveAndClose() {
        String url = relayField.getValue().trim();
        if (!url.isBlank() && !url.startsWith("wss://") && !url.startsWith("ws://")) {
            statusMessage = "§cURL must start with wss:// or ws://";
            return;
        }
        VoxelPortMod.getConfig().setRelayUrl(url.isBlank() ? null : url);
        VoxelPortMod.getConfig().save();
        this.minecraft.setScreen(parent);
    }

    private void testConnection() {
        if (testing) return;
        String raw = relayField.getValue().trim();
        String url = raw.isBlank() ? RelayUrlResolver.get() : raw;

        if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
            statusMessage = "§cURL must start with wss:// or ws://";
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

        String hint = "Leave blank to use the VoxelPort default relay.";
        gui.text(this.font, hint, cx - 150, 158, 0xFF606060, true);

        relayField.extractWidgetRenderState(gui, mouseX, mouseY, delta);

        if (!statusMessage.isEmpty()) {
            gui.text(this.font, statusMessage,
                    cx - this.font.width(statusMessage) / 2, 176, 0xFFFFFF55, true);
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
