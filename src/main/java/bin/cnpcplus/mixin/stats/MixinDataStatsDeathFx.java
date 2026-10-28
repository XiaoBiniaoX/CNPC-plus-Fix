package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.fx.FxStore;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataStats.class, remap = false)
public abstract class MixinDataStatsDeathFx {
    @Inject(method = "readToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readDeathFx(NBTTagCompound tag, CallbackInfo ci) { FxStore.read(this, tag, FxStore.DEATH); }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeDeathFx(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        FxStore.write(this, cir.getReturnValue(), FxStore.DEATH);
    }
}
