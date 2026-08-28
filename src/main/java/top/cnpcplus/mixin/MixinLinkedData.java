package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.LinkedNpcController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = LinkedNpcController.LinkedData.class, remap = false)
public class MixinLinkedData {

    @Shadow(remap = false)
    public String name;

    @Shadow(remap = false)
    public CompoundTag data;

    @Inject(method = "setNBT", at = @At("TAIL"), remap = false)
    private void cnpcplus$readSyncScripts(CompoundTag compound, CallbackInfo ci) {
        ExtraDataStorage.setBool(this, compound.getBoolean("SyncScripts"));
    }

    @Inject(method = "getNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeSyncScripts(CallbackInfoReturnable<CompoundTag> cir) {
        if (ExtraDataStorage.getBool(this)) {
            cir.getReturnValue().putBoolean("SyncScripts", true);
        }
    }
}