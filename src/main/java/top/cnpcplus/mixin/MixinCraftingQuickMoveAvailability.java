package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerCarpentryBench;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {CraftingMenu.class, ContainerCarpentryBench.class}, remap = false)
public abstract class MixinCraftingQuickMoveAvailability {
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$pickup(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index == 0 && !((AbstractContainerMenu)(Object)this).getSlot(0).mayPickup(player)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
