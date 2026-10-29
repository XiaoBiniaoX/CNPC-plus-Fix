package top.cnpcplus.mixin;

import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.villagertrade.EditMode;

/**
 * 让结果槽在编辑模式下可放置物品。
 */
@Mixin(MerchantResultSlot.class)
public class MixinMerchantResultSlotEditable {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$allowPlaceInEditMode(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (EditMode.active) {
            cir.setReturnValue(true);
        }
    }
}
