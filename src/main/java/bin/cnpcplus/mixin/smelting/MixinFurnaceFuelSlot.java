package bin.cnpcplus.mixin.smelting;

import net.minecraft.world.inventory.FurnaceFuelSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 允许编辑/运行中的三种原版炉子燃料槽接收任意非空物品。 */
@Mixin(value = FurnaceFuelSlot.class, remap = false)
public class MixinFurnaceFuelSlot {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$allowAnyFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(stack != null && !stack.isEmpty());
    }
}
