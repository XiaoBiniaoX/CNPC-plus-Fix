package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.puppet.PartConfigAccessor;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Offset/scale storage on every JobPuppet.PartConfig (body + equip).
 * Public fields so render layers can read via reflection without interface cast issues.
 */
@Mixin(value = noppes.npcs.roles.JobPuppet.PartConfig.class, remap = false)
public abstract class MixinPuppetPartConfig implements PartConfigAccessor {

    // public for reliable reflection from early Layer mixins
    @Unique public float cnpcplusOffsetX;
    @Unique public float cnpcplusOffsetY;
    @Unique public float cnpcplusOffsetZ;
    @Unique public float cnpcplusScaleX = 1.0f;
    @Unique public float cnpcplusScaleY = 1.0f;
    @Unique public float cnpcplusScaleZ = 1.0f;

    @Override
    public float cnpcplus$getOffsetX() { return this.cnpcplusOffsetX; }
    @Override
    public float cnpcplus$getOffsetY() { return this.cnpcplusOffsetY; }
    @Override
    public float cnpcplus$getOffsetZ() { return this.cnpcplusOffsetZ; }
    @Override
    public void cnpcplus$setOffsetX(float x) { this.cnpcplusOffsetX = clampOff(x); }
    @Override
    public void cnpcplus$setOffsetY(float y) { this.cnpcplusOffsetY = clampOff(y); }
    @Override
    public void cnpcplus$setOffsetZ(float z) { this.cnpcplusOffsetZ = clampOff(z); }
    @Override
    public float cnpcplus$getScaleX() { return this.cnpcplusScaleX; }
    @Override
    public float cnpcplus$getScaleY() { return this.cnpcplusScaleY; }
    @Override
    public float cnpcplus$getScaleZ() { return this.cnpcplusScaleZ; }
    @Override
    public void cnpcplus$setScaleX(float x) { this.cnpcplusScaleX = clampSc(x); }
    @Override
    public void cnpcplus$setScaleY(float y) { this.cnpcplusScaleY = clampSc(y); }
    @Override
    public void cnpcplus$setScaleZ(float z) { this.cnpcplusScaleZ = clampSc(z); }

    @Unique
    private static float clampOff(float v) {
        if (v < -20.0f) return -20.0f;
        if (v > 20.0f) return 20.0f;
        return v;
    }

    @Unique
    private static float clampSc(float v) {
        if (v < 0.01f) return 0.01f;
        if (v > 10.0f) return 10.0f;
        return v;
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeOffset(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound tag = cir.getReturnValue();
        if (tag == null) return;
        tag.setFloat("PuppetOffsetX", this.cnpcplusOffsetX);
        tag.setFloat("PuppetOffsetY", this.cnpcplusOffsetY);
        tag.setFloat("PuppetOffsetZ", this.cnpcplusOffsetZ);
        tag.setFloat("PuppetScaleX", this.cnpcplusScaleX);
        tag.setFloat("PuppetScaleY", this.cnpcplusScaleY);
        tag.setFloat("PuppetScaleZ", this.cnpcplusScaleZ);
    }

    @Inject(method = "readNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readOffset(NBTTagCompound tag, CallbackInfo ci) {
        if (tag == null) return;
        this.cnpcplusOffsetX = tag.getFloat("PuppetOffsetX");
        this.cnpcplusOffsetY = tag.getFloat("PuppetOffsetY");
        this.cnpcplusOffsetZ = tag.getFloat("PuppetOffsetZ");
        this.cnpcplusScaleX = tag.hasKey("PuppetScaleX") ? tag.getFloat("PuppetScaleX") : 1.0f;
        this.cnpcplusScaleY = tag.hasKey("PuppetScaleY") ? tag.getFloat("PuppetScaleY") : 1.0f;
        this.cnpcplusScaleZ = tag.hasKey("PuppetScaleZ") ? tag.getFloat("PuppetScaleZ") : 1.0f;
    }
}
