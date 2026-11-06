package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemCombinerMenu.class, remap = false)
public abstract class MixinCombinerQuickMoveAvailability {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$pickup(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        ItemCombinerMenu menu = (ItemCombinerMenu)(Object)this;
        if (index == menu.getResultSlot() && !menu.getSlot(index).mayPickup(player)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
