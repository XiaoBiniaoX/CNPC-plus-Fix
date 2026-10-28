package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IDataStatsHideDelayAccess;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 238-2：尸体隐藏延迟（tick），随 DataStats 的 NBT 持久化。
 * 原版写死 20 tick，这里默认 5，允许 0..200。
 */
@Mixin(value = DataStats.class, remap = false)
public abstract class MixinDataStatsHideDelay implements IDataStatsHideDelayAccess {

    @Unique
    private int cnpcplus$hideDelay = 5;

    @Inject(method = "readToNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("TAIL"))
    private void cnpcplus$readHideDelay(NBTTagCompound compound, CallbackInfo ci) {
        cnpcplus$hideDelay = compound.hasKey("CNPCPlusHideDelay", 99)
                ? cnpcplus$clamp(compound.getInteger("CNPCPlusHideDelay"))
                : 5;
    }

    @Inject(method = "writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
            at = @At("RETURN"))
    private void cnpcplus$writeHideDelay(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound out = cir.getReturnValue();
        if (out != null) {
            out.setInteger("CNPCPlusHideDelay", cnpcplus$hideDelay);
        }
    }

    @Override
    public int cnpcplus$getHideDelay() {
        return cnpcplus$hideDelay;
    }

    @Override
    public void cnpcplus$setHideDelay(int delay) {
        this.cnpcplus$hideDelay = cnpcplus$clamp(delay);
    }

    @Unique
    private static int cnpcplus$clamp(int delay) {
        if (delay < 0) return 0;
        return delay > 200 ? 200 : delay;
    }
}
