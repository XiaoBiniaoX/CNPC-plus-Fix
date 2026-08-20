package bin.cnpcplus.mixin.mount;

import bin.cnpcplus.common.IMountControlData;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;

/** Adds the high-version MountControl value without changing the CNPC common classes. */
@Mixin(value = DataAI.class, remap = false)
public abstract class MixinDataAIMountControl implements IMountControlData {
    @Unique
    private boolean cnpcplus$mountControl;

    @Override
    public boolean cnpcplus$getMountControl() {
        return this.cnpcplus$mountControl;
    }

    @Override
    public void cnpcplus$setMountControl(boolean enabled) {
        this.cnpcplus$mountControl = enabled;
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), remap = false)
    private void cnpcplus$readMountControl(NBTTagCompound compound, CallbackInfo ci) {
        this.cnpcplus$mountControl = compound.getBoolean("MountControl");
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeMountControl(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        compound.setBoolean("MountControl", this.cnpcplus$mountControl);
    }
}
