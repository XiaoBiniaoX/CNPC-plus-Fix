package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.PursuitLimit;

@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedPursuit implements PursuitLimit {
    @Unique private boolean cnpcplus$pursuitLimited;
    @Unique private float cnpcplus$pursuitRadius = 32.0F;

    @Override public boolean cnpcplus$isPursuitLimited() { return cnpcplus$pursuitLimited; }
    @Override public void cnpcplus$setPursuitLimited(boolean enabled) { cnpcplus$pursuitLimited = enabled; }
    @Override public float cnpcplus$getPursuitRadius() { return cnpcplus$pursuitRadius; }
    @Override public void cnpcplus$setPursuitRadius(float radius) {
        cnpcplus$pursuitRadius = Float.isFinite(radius) ? Math.max(0.0F, Math.min(32.0F, radius)) : 32.0F;
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$savePursuit(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        tag.putBoolean("CNPCPlusPursuitLimited", cnpcplus$pursuitLimited);
        tag.putFloat("CNPCPlusPursuitRadius", cnpcplus$pursuitRadius);
    }

    @Inject(method = "readToNBT", at = @At("RETURN"))
    private void cnpcplus$loadPursuit(CompoundTag tag, CallbackInfo ci) {
        cnpcplus$pursuitLimited = tag.getBoolean("CNPCPlusPursuitLimited");
        cnpcplus$setPursuitRadius(tag.contains("CNPCPlusPursuitRadius", 99) ? tag.getFloat("CNPCPlusPursuitRadius") : 32.0F);
    }
}
