package com.voxelport.mod.mixin;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.server.ServerRelayService;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerTransferMixin {
    @Inject(method = "acceptsTransfers", at = @At("HEAD"), cancellable = true)
    private void voxelport$acceptTransfersWhileHosting(CallbackInfoReturnable<Boolean> cir) {
        ServerRelayService serverService = VoxelPortMod.getServerRelayService();
        if (serverService != null && serverService.isRunning()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void voxelport$stopRelayOnServerClose(CallbackInfo ci) {
        ServerRelayService serverService = VoxelPortMod.getServerRelayService();
        if (serverService != null && serverService.isRunning()) {
            serverService.stop();
        }
    }
}
