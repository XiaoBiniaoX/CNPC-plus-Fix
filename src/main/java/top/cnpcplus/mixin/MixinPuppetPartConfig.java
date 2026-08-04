package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.puppet.PartConfigAccessor;

@Mixin(JobPuppet.PartConfig.class)
public abstract class MixinPuppetPartConfig implements PartConfigAccessor {
    @Unique private float cnpcplus$puppetOffsetX;
    @Unique private float cnpcplus$puppetOffsetY;
    @Unique private float cnpcplus$puppetOffsetZ;

    @Override public float cnpcplus$getOffsetX() { return this.cnpcplus$puppetOffsetX; }
    @Override public float cnpcplus$getOffsetY() { return this.cnpcplus$puppetOffsetY; }
    @Override public float cnpcplus$getOffsetZ() { return this.cnpcplus$puppetOffsetZ; }
    @Override public void cnpcplus$setOffsetX(float x) { this.cnpcplus$puppetOffsetX = cnpcplus$clampOffset(x); }
    @Override public void cnpcplus$setOffsetY(float y) { this.cnpcplus$puppetOffsetY = cnpcplus$clampOffset(y); }
    @Override public void cnpcplus$setOffsetZ(float z) { this.cnpcplus$puppetOffsetZ = cnpcplus$clampOffset(z); }

    @Unique
    private static float cnpcplus$clampOffset(float v) {
        if (v < -20.0f) return -20.0f;
        if (v > 20.0f) return 20.0f;
        return v;
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeOffset(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putFloat("PuppetOffsetX", this.cnpcplus$puppetOffsetX);
        tag.putFloat("PuppetOffsetY", this.cnpcplus$puppetOffsetY);
        tag.putFloat("PuppetOffsetZ", this.cnpcplus$puppetOffsetZ);
    }

    @Inject(method = "readNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readOffset(CompoundTag tag, CallbackInfo ci) {
        this.cnpcplus$puppetOffsetX = tag.getFloat("PuppetOffsetX");
        this.cnpcplus$puppetOffsetY = tag.getFloat("PuppetOffsetY");
        this.cnpcplus$puppetOffsetZ = tag.getFloat("PuppetOffsetZ");
    }
}
