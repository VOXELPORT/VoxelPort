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
    /**
     * Forces acceptsTransfers() to true while a VoxelPort tunnel is active, so
     * connections arriving through the relay aren't rejected by a server that
     * hasn't explicitly opted into the transfer-packet feature.
     *
     * Known limitation: this applies server-wide for as long as hosting is
     * active, not just to relay-proxied connections — acceptsTransfers() gives
     * the mixin no way to tell which specific incoming connection is asking,
     * short of a deeper network-layer change to tag connection origin (out of
     * scope here). If this server is ALSO reachable directly (not just
     * through VoxelPort) and `accepts-transfers=false` in server.properties is
     * being relied on as its own control for those direct connections, that
     * control is bypassed for as long as VoxelPort hosting is running.
     */
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
