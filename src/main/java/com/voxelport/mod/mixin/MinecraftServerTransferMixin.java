package com.voxelport.mod.mixin;

import com.voxelport.mod.VoxelPortMod;
import com.voxelport.mod.logic.HostingService;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public class MinecraftServerTransferMixin {
    @Inject(method = "acceptsTransfers", at = @At("HEAD"), cancellable = true)
    private void voxelport$acceptTransfersWhileHosting(CallbackInfoReturnable<Boolean> cir) {
        HostingService service = VoxelPortMod.getHostingService();
        if (service != null && service.isRunning()) {
            cir.setReturnValue(true);
        }
    }
}
