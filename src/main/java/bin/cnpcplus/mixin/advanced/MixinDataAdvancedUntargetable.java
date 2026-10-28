package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.common.IUntargetableAccess;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 238-12：阵营页「不会被 NPC 作为攻击目标」开关，随 DataAdvanced 持久化。 */
@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedUntargetable implements IUntargetableAccess {

    @Unique
    private boolean cnpcplus$untargetable;

    @Override
    public boolean cnpcplus$isUntargetable() {
        return this.cnpcplus$untargetable;
    }

    @Override
    public void cnpcplus$setUntargetable(boolean untargetable) {
        this.cnpcplus$untargetable = untargetable;
    }

    @Inject(method = "readToNBT", at = @At("RETURN"))
    private void cnpcplus$readUntargetable(NBTTagCompound compound, CallbackInfo ci) {
        this.cnpcplus$untargetable = compound.getBoolean("CNPCPlusUntargetable");
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$writeUntargetable(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound out = cir.getReturnValue();
        if (out != null) {
            out.setBoolean("CNPCPlusUntargetable", this.cnpcplus$untargetable);
        }
    }
}
