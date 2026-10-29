package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.ItemCost;
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
@Mixin(value = MerchantOffer.class, remap = false)
public abstract class MixinMerchantOfferNameOnly implements NameOnlyOffer {

    @Unique
    private boolean cnpcplus$nameOnly;
    @Unique private boolean cnpcplus$customOffer;

    @Override
    public void cnpcplus$setNameOnly(boolean value) {
        this.cnpcplus$nameOnly = value;
        this.cnpcplus$customOffer = true;
    }

    @Override
    public boolean cnpcplus$isNameOnly() {
        return this.cnpcplus$nameOnly;
    }

    @Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$nameOnlyMatch(ItemStack givenA, ItemStack givenB, CallbackInfoReturnable<Boolean> cir) {
        if (!cnpcplus$customOffer) return;
        MerchantOffer offer = (MerchantOffer) (Object) this;
        ItemStack costA = offer.getCostA();
        ItemStack costB = offer.getCostB();
        // 新版 ItemCost 不再支持旧版 isRequiredItem；仅本模组条目保留空槽和数量语义。
        cir.setReturnValue(cnpcplus$matchesCost(givenA, costA, offer.getItemCostA())
                && cnpcplus$matchesCost(givenB, costB, offer.getItemCostB().orElse(null)));
    }

    @Unique
    private boolean cnpcplus$matchesCost(ItemStack given, ItemStack cost, ItemCost requirement) {
        if (cost.isEmpty()) return given.isEmpty();
        if (given.isEmpty() || given.getCount() < cost.getCount()) return false;
        return cnpcplus$nameOnly
                ? given.getItem() == cost.getItem() && given.getHoverName().getString().equals(cost.getHoverName().getString())
                : requirement != null && requirement.test(given);
    }
}
