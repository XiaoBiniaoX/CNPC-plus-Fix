package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.ai.PursuitLimit;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataAdvanced.class, remap = false)
public class MixinDataAdvancedPursuit implements PursuitLimit {
    @Unique private boolean cnpcplus$pursuitLimited;
    @Unique private float cnpcplus$pursuitRadius = 32.0F;
    public boolean cnpcplus$isPursuitLimited() { return cnpcplus$pursuitLimited; }
    public void cnpcplus$setPursuitLimited(boolean enabled) { cnpcplus$pursuitLimited = enabled; }
    public float cnpcplus$getPursuitRadius() { return cnpcplus$pursuitRadius; }
    public void cnpcplus$setPursuitRadius(float radius) { cnpcplus$pursuitRadius = Float.isFinite(radius) ? Math.max(0, Math.min(32, radius)) : 32; }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$save(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        tag.setBoolean("CNPCPlusPursuitLimited", cnpcplus$pursuitLimited);
        tag.setFloat("CNPCPlusPursuitRadius", cnpcplus$pursuitRadius);
    }
    @Inject(method = "readToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$load(NBTTagCompound tag, CallbackInfo ci) {
        cnpcplus$pursuitLimited = tag.getBoolean("CNPCPlusPursuitLimited");
        cnpcplus$setPursuitRadius(tag.hasKey("CNPCPlusPursuitRadius", 99) ? tag.getFloat("CNPCPlusPursuitRadius") : 32);
    }
}
