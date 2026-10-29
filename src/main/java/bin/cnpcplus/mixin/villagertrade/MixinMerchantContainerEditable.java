package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.world.inventory.MerchantContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.villagertrade.EditableMerchant;

/**
 * 编辑模式下禁止 updateSellItem 用 offer 自动重算/清空结果槽。
 * 该方法是"结果槽物品被吞"的单点根因：槽0空→setItem(2,EMPTY)清空；
 * 槽0/1有匹配→setItem(2,offer.assemble())覆盖；触发源是 setChanged/setItem(0,1)/setSelectionHint。
 */
@Mixin(value = MerchantContainer.class, remap = false)
public class MixinMerchantContainerEditable {
    @Shadow @Final private Merchant merchant;

    @Inject(method = "updateSellItem", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$editableSkipUpdateSellItem(CallbackInfo ci) {
        if (merchant instanceof EditableMerchant) {
            ci.cancel();
        }
    }
}
