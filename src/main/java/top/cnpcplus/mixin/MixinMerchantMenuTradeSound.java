package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 根因（阶段225，测试包日志每次 shift 拿结果刷整段 ClassCastException）：
 * 原版 MerchantMenu.playTradeSound() 在 !merchant.isClientSide() 时把 merchant 强转 Entity
 * 播交易音效——原版商人（AbstractVillager/WanderingTrader）都是实体，本 mod 的
 * SimpleMerchant / EditableMerchant 是纯接口实现 → shift 拿结果槽时 CCE：
 * 抛点在 onTake（扣 uses/触发限购）之前 → 整个 shift 购买被中断且服务端每次刷堆栈。
 * 修法：非 Entity 商人取消原版音效（哈基彬 225 反馈：本 mod 商人交易不要音效 = 全程静音）；
 * Entity 商人完全放行 = 原版行为不变。
 * 商人字段经 MerchantMenuAccessor 取（@Shadow trader 被 AP 静默跳过 refmap，见 225 记录）。
 */
@Mixin(MerchantMenu.class)
public class MixinMerchantMenuTradeSound {

    @Inject(method = "playTradeSound", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$skipCastForNonEntityTrader(CallbackInfo ci) {
        Merchant trader = ((MerchantMenuAccessor) (Object) this).getTrader();
        if (!(trader instanceof Entity)) {
            ci.cancel();
        }
    }
}
