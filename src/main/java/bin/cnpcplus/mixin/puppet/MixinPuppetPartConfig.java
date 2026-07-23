package bin.cnpcplus.mixin.puppet;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.roles.JobPuppet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import bin.cnpcplus.puppet.PartConfigAccessor;

@Mixin(JobPuppet.PartConfig.class)
public abstract class MixinPuppetPartConfig implements PartConfigAccessor {
    @Unique private float cnpcplus$puppetOffsetX;
    @Unique private float cnpcplus$puppetOffsetY;
    @Unique private float cnpcplus$puppetOffsetZ;
    @Unique private float cnpcplus$puppetScaleX = 1.0f;
    @Unique private float cnpcplus$puppetScaleY = 1.0f;
    @Unique private float cnpcplus$puppetScaleZ = 1.0f;

    @Override public float cnpcplus$getOffsetX() { return this.cnpcplus$puppetOffsetX; }
    @Override public float cnpcplus$getOffsetY() { return this.cnpcplus$puppetOffsetY; }
    @Override public float cnpcplus$getOffsetZ() { return this.cnpcplus$puppetOffsetZ; }
    @Override public void cnpcplus$setOffsetX(float x) { this.cnpcplus$puppetOffsetX = cnpcplus$clampOffset(x); }
    @Override public void cnpcplus$setOffsetY(float y) { this.cnpcplus$puppetOffsetY = cnpcplus$clampOffset(y); }
    @Override public void cnpcplus$setOffsetZ(float z) { this.cnpcplus$puppetOffsetZ = cnpcplus$clampOffset(z); }
    @Override public float cnpcplus$getScaleX() { return this.cnpcplus$puppetScaleX; }
    @Override public float cnpcplus$getScaleY() { return this.cnpcplus$puppetScaleY; }
    @Override public float cnpcplus$getScaleZ() { return this.cnpcplus$puppetScaleZ; }
    @Override public void cnpcplus$setScaleX(float x) { this.cnpcplus$puppetScaleX = cnpcplus$clampScale(x); }
    @Override public void cnpcplus$setScaleY(float y) { this.cnpcplus$puppetScaleY = cnpcplus$clampScale(y); }
    @Override public void cnpcplus$setScaleZ(float z) { this.cnpcplus$puppetScaleZ = cnpcplus$clampScale(z); }

    @Unique
    private static float cnpcplus$clampOffset(float v) {
        if (v < -20.0f) return -20.0f;
        if (v > 20.0f) return 20.0f;
        return v;
    }

    @Unique
    private static float cnpcplus$clampScale(float v) {
        if (v < 0.01f) return 0.01f;
        if (v > 10.0f) return 10.0f;
        return v;
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeOffset(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putFloat("PuppetOffsetX", this.cnpcplus$puppetOffsetX);
        tag.putFloat("PuppetOffsetY", this.cnpcplus$puppetOffsetY);
        tag.putFloat("PuppetOffsetZ", this.cnpcplus$puppetOffsetZ);
        tag.putFloat("PuppetScaleX", this.cnpcplus$puppetScaleX);
        tag.putFloat("PuppetScaleY", this.cnpcplus$puppetScaleY);
        tag.putFloat("PuppetScaleZ", this.cnpcplus$puppetScaleZ);
    }

    @Inject(method = "readNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readOffset(CompoundTag tag, CallbackInfo ci) {
        this.cnpcplus$puppetOffsetX = tag.getFloat("PuppetOffsetX");
        this.cnpcplus$puppetOffsetY = tag.getFloat("PuppetOffsetY");
        this.cnpcplus$puppetOffsetZ = tag.getFloat("PuppetOffsetZ");
        this.cnpcplus$puppetScaleX = tag.contains("PuppetScaleX") ? tag.getFloat("PuppetScaleX") : 1.0f;
        this.cnpcplus$puppetScaleY = tag.contains("PuppetScaleY") ? tag.getFloat("PuppetScaleY") : 1.0f;
        this.cnpcplus$puppetScaleZ = tag.contains("PuppetScaleZ") ? tag.getFloat("PuppetScaleZ") : 1.0f;
    }
}
