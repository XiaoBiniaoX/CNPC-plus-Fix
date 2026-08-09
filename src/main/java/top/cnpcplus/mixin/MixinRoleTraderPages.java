package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.trader.TraderPager;

@Mixin(value = RoleTrader.class, remap = false)
public class MixinRoleTraderPages {

    @Inject(method = "writeNBT", at = @At("HEAD"))
    private void cnpcplus$flushPages(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        TraderPager.flushCurrent((RoleTrader) (Object) this);
    }

    @Inject(method = "writeNBT", at = @At("RETURN"))
    private void cnpcplus$writePages(CompoundTag nbttagcompound, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag pages = TraderPager.toNBT((RoleTrader) (Object) this);
        if (pages != null) {
            nbttagcompound.put("TraderPages", pages.getList("TraderPages", 10));
            if (pages.contains("PageTitles")) {
                nbttagcompound.put("PageTitles", pages.get("PageTitles"));
            }
            if (pages.contains("FullPages")) {
                nbttagcompound.putBoolean("FullPages", pages.getBoolean("FullPages"));
            }
        }
    }

    @Inject(method = "readNBT", at = @At("RETURN"))
    private void cnpcplus$readPages(CompoundTag nbttagcompound, CallbackInfo ci) {
        TraderPager.fromNBT((RoleTrader) (Object) this, nbttagcompound);
    }
}
