package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 对齐终版：接口商人不强转 Entity、不播放交易音效；原版实体商人不变。 */
@Mixin(value = MerchantMenu.class, remap = false)
public class MixinMerchantMenuTradeSound {
    @Inject(method = "playTradeSound", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$skipCastForNonEntityTrader(CallbackInfo ci) {
        if (!(((MerchantMenuAccessor) this).getTrader() instanceof Entity)) ci.cancel();
    }
}
