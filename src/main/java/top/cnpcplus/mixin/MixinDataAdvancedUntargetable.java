package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.UntargetableAccess;

@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedUntargetable implements UntargetableAccess {
    @Unique private boolean cnpcplus$untargetable;

    @Override
    public boolean cnpcplus$isUntargetable() {
        return this.cnpcplus$untargetable;
    }

    @Override
    public void cnpcplus$setUntargetable(boolean untargetable) {
        this.cnpcplus$untargetable = untargetable;
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveUntargetable(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        tag.putBoolean("CNPCPlusUntargetable", this.cnpcplus$untargetable);
    }

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$loadUntargetable(CompoundTag tag, CallbackInfo ci) {
        this.cnpcplus$untargetable = tag.getBoolean("CNPCPlusUntargetable");
    }
}
