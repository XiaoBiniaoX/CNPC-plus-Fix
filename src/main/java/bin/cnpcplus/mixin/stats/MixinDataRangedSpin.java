package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IRangedSpinAccess;
import bin.cnpcplus.common.ProjectileSpinKeys;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 238-9：与 1.20.1 相同的 XYZ 自旋系数；全部为零时保留原版。 */
@Mixin(value = DataRanged.class, remap = false)
public abstract class MixinDataRangedSpin implements IRangedSpinAccess {

    @Unique
    private final float[] cnpcplus$spinSpeed = new float[3];

    @Override
    public float cnpcplus$getSpinSpeed(int axis) {
        return axis >= 0 && axis < 3 ? this.cnpcplus$spinSpeed[axis] : 0.0F;
    }

    @Override
    public void cnpcplus$setSpinSpeed(int axis, float speed) {
        if (axis >= 0 && axis < 3) this.cnpcplus$spinSpeed[axis] = ProjectileSpinKeys.clamp(speed);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void cnpcplus$readSpinSpeed(NBTTagCompound compound, CallbackInfo ci) {
        for (int axis = 0; axis < 3; axis++) {
            this.cnpcplus$setSpinSpeed(axis, compound.getFloat(ProjectileSpinKeys.NBT_KEYS[axis]));
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$writeSpinSpeed(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound out = cir.getReturnValue();
        if (out != null) {
            for (int axis = 0; axis < 3; axis++) {
                out.setFloat(ProjectileSpinKeys.NBT_KEYS[axis], this.cnpcplus$spinSpeed[axis]);
            }
        }
    }
}
