package bin.cnpcplus.mixin.villagertrade;

import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.villagertrade.EditableMerchant;

/**
 * 让结果槽在编辑模式下可放置物品。
 */
@Mixin(value = MerchantResultSlot.class, remap = false)
public class MixinMerchantResultSlotEditable {
    @Shadow @Final private Merchant merchant;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$allowPlaceInEditMode(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (merchant instanceof EditableMerchant) {
            cir.setReturnValue(true);
        }
    }
}
