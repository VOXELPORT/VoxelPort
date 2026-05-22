package com.voxelport.mod.mixin;

import com.voxelport.mod.client.gui.JoinCodeScreen;
import com.voxelport.mod.client.gui.SettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // "Join via VoxelPort" takes up 158px, then a 4px gap, then a 42px "⚙" settings button.
        // Total = 204px, same as the original single button, so layout is unchanged.
        this.addRenderableWidget(Button.builder(Component.literal("Join via VoxelPort"), button ->
                this.minecraft.setScreen(new JoinCodeScreen(this)))
                .bounds(this.width / 2 - 102, 5, 158, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("⚙"), button ->
                this.minecraft.setScreen(new SettingsScreen(this)))
                .bounds(this.width / 2 + 60, 5, 42, 20)
                .build());
    }
}
