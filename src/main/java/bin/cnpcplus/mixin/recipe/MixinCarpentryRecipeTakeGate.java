package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.craftingview.RecipeAvailability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import noppes.npcs.containers.ContainerCarpentryBench;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ContainerCarpentryBench.class, remap = false)
public abstract class MixinCarpentryRecipeTakeGate {
    @Inject(method = "func_82846_b", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$shiftGate(EntityPlayer player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != 0 || player.world.isRemote) return;
        ContainerCarpentryBench container = (ContainerCarpentryBench)(Object)this;
        RecipeAvailability.refreshCrafting(container);
        if (!container.getSlot(0).getHasStack()) cir.setReturnValue(ItemStack.EMPTY);
    }
}
