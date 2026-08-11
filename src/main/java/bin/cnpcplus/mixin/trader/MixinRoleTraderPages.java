package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.trader.TraderPager;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Persistence hooks: flush the current page back into the page cache before
 * writeNBT, merge the page data into the role NBT on write, and rebuild the
 * cache from NBT on read. Old saves without page keys stay compatible.
 */
@Mixin(RoleTrader.class)
public class MixinRoleTraderPages {

    @Inject(method = "writeNBT", at = @At("HEAD"), remap = false)
    private void cnpcplus$flushPages(NBTTagCompound nbttagcompound, CallbackInfoReturnable<NBTTagCompound> cir) {
        TraderPager.flushCurrent((RoleTrader) (Object) this);
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writePages(NBTTagCompound nbttagcompound, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound pages = TraderPager.toNBT((RoleTrader) (Object) this);
        if (pages != null) {
            nbttagcompound.setTag("TraderPages", pages.getTagList("TraderPages", 10));
            if (pages.hasKey("PageTitles")) {
                nbttagcompound.setTag("PageTitles", pages.getTag("PageTitles"));
            }
            if (pages.hasKey("FullPages")) {
                nbttagcompound.setBoolean("FullPages", pages.getBoolean("FullPages"));
            }
        }
    }

    @Inject(method = "readNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readPages(NBTTagCompound nbttagcompound, CallbackInfo ci) {
        TraderPager.fromNBT((RoleTrader) (Object) this, nbttagcompound);
    }
}
