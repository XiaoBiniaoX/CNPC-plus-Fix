package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.stats.DeathFxStore;
import top.cnpcplus.stats.HideDelayStore;

/**
 * 238-2/3：把尸体隐藏延迟与死亡粒子两键挂到 DataStats 的 save/readToNBT RETURN，
 * 随 STATS 菜单包双向同步（与 MixinDataStatsFloat 同一手法，白拿客户端同步）。
 * 键：{@code CNPCPlusHideDelay}（int）、{@code CNPCPlusDeathFx}（ListTag）。
 */
@Mixin(value = DataStats.class, remap = false)
public class MixinDataStatsExtras {

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveExtras(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound = cir.getReturnValue();
        int delay = HideDelayStore.get((DataStats) (Object) this);
        if (delay != HideDelayStore.DEFAULT_DELAY) {
            compound.putInt("CNPCPlusHideDelay", delay);
        }
        DeathFxStore.write((DataStats) (Object) this, compound);
    }

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readExtras(CompoundTag compound, CallbackInfo ci) {
        DataStats stats = (DataStats) (Object) this;
        if (compound.contains("CNPCPlusHideDelay", Tag.TAG_ANY_NUMERIC)) {
            HideDelayStore.put(stats, compound.getInt("CNPCPlusHideDelay"));
        } else {
            HideDelayStore.clear(stats);
        }
        DeathFxStore.read(stats, compound);
    }
}
