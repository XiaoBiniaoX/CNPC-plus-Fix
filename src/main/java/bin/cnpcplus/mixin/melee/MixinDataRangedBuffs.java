package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.RangedBuffStore;
import bin.cnpcplus.fx.FxStore;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 238-4：远程多效果随 DataRanged 的 MainmenuStatsSave 一起存取。 */
@Mixin(value = DataRanged.class, remap = false)
public abstract class MixinDataRangedBuffs {

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void cnpcplus$readBuffs(NBTTagCompound compound, CallbackInfo ci) {
        RangedBuffStore.read((DataRanged) (Object) this, compound);
        FxStore.read(this, compound, FxStore.PROJECTILE);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$writeBuffs(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        RangedBuffStore.write((DataRanged) (Object) this, cir.getReturnValue());
        FxStore.write(this, cir.getReturnValue(), FxStore.PROJECTILE);
    }
}
