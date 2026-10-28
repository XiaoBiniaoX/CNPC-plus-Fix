package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.common.IDamageInspectionAccess;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 238-13：阵营页「视察」开关与观察距离，随 DataAdvanced 持久化。 */
@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedDamageInspection implements IDamageInspectionAccess {

    @Unique
    private boolean cnpcplus$damageInspection;

    @Unique
    private int cnpcplus$inspectionDistance = 3;

    @Override
    public boolean cnpcplus$isDamageInspectionEnabled() {
        return this.cnpcplus$damageInspection;
    }

    @Override
    public void cnpcplus$setDamageInspectionEnabled(boolean enabled) {
        this.cnpcplus$damageInspection = enabled;
    }

    @Override
    public int cnpcplus$getInspectionDistance() {
        return this.cnpcplus$inspectionDistance;
    }

    @Override
    public void cnpcplus$setInspectionDistance(int distance) {
        this.cnpcplus$inspectionDistance = Math.max(1, Math.min(16, distance));
    }

    @Inject(method = "readToNBT", at = @At("RETURN"))
    private void cnpcplus$readInspection(NBTTagCompound compound, CallbackInfo ci) {
        this.cnpcplus$damageInspection = compound.getBoolean("CNPCPlusDamageInspection");
        this.cnpcplus$inspectionDistance = compound.hasKey("CNPCPlusInspectionDistance", 99)
                ? Math.max(1, Math.min(16, compound.getInteger("CNPCPlusInspectionDistance")))
                : 3;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$writeInspection(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound out = cir.getReturnValue();
        if (out != null) {
            out.setBoolean("CNPCPlusDamageInspection", this.cnpcplus$damageInspection);
            out.setInteger("CNPCPlusInspectionDistance", this.cnpcplus$inspectionDistance);
        }
    }
}
