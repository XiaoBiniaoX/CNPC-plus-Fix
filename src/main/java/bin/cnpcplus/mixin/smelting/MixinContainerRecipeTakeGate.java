package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.craftingview.RecipeAvailability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Container.class)
public abstract class MixinContainerRecipeTakeGate {
    @Inject(method = "slotClick", at = @At("HEAD"), require = 1)
    private void cnpcplus$refreshBeforeTake(int slot, int button, ClickType type, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (!player.world.isRemote && slot == 0) RecipeAvailability.refreshCrafting((Container)(Object)this);
    }
}
