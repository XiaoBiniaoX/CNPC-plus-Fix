package top.cnpcplus.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerCarpentryBench.class)
public class MixinContainerCarpentryBenchFix2 {
    @Inject(method = "m_7648_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$take(Player player, int slot, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 0 && !((ContainerCarpentryBench)(Object)this).getSlot(0).mayPickup(player)) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Shadow(remap = false)
    private Player player;

    @Inject(method = "m_6199_", at = @At("RETURN"), remap = false)
    private void cnpcplus$ensureCarpentryResult(Container container, CallbackInfo ci) {
        ContainerCarpentryBench bench = (ContainerCarpentryBench)(Object)this;
        RecipeCarpentry recipe = RecipeController.instance.findMatchingRecipe(bench.craftMatrix);
        if (recipe == null || !recipe.availability.isAvailable(this.player)) {
            if (!bench.craftResult.getItem(0).isEmpty()) {
                bench.craftResult.setItem(0, ItemStack.EMPTY);
                ((AbstractContainerMenu)(Object)this).broadcastChanges();
            }
        } else {
            ItemStack result = recipe.getResult();
            if (result != null && !ItemStack.isSameItemSameTags(bench.craftResult.getItem(0), result)) {
                bench.craftResult.setItem(0, result.copy());
                ((AbstractContainerMenu)(Object)this).broadcastChanges();
            }
        }
    }
}
