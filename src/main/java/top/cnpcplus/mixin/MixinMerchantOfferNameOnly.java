package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.villagertrade.NameOnlyOffer;

/**
 * 仅名字检查（哈基彬需求）：mod 物品 NBT 每次变化时，vanilla isRequiredItem 的
 * isSameItemSameTags 比对判不匹配 → 交易永远不可用。nameOnly 条目只比 id + 展示名。
 * 数量检查在外层 satisfiedBy（given.count >= cost.count）保留，不受影响。
 * 空槽对（双方 empty / 单空）不拦，走 vanilla 语义（true/false）。
 */
@Mixin(MerchantOffer.class)
public abstract class MixinMerchantOfferNameOnly implements NameOnlyOffer {

    @Unique
    private boolean cnpcplus$nameOnly;

    @Override
    public void cnpcplus$setNameOnly(boolean value) {
        this.cnpcplus$nameOnly = value;
    }

    @Override
    public boolean cnpcplus$isNameOnly() {
        return this.cnpcplus$nameOnly;
    }

    @Inject(method = {"isRequiredItem", "m_45365_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$nameOnlyMatch(ItemStack given, ItemStack cost, CallbackInfoReturnable<Boolean> cir) {
        if (this.cnpcplus$nameOnly && !given.isEmpty() && !cost.isEmpty()) {
            cir.setReturnValue(given.getItem() == cost.getItem()
                && given.getHoverName().getString().equals(cost.getHoverName().getString()));
        }
    }
}
