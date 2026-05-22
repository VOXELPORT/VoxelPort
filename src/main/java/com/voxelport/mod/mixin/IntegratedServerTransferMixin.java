package com.voxelport.mod.mixin;

import com.voxelport.mod.VoxelPortMod;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public class IntegratedServerTransferMixin {
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        if ((Object) this instanceof IntegratedServer) {
            VoxelPortMod.getHostingService().stop();
        }
    }
}
