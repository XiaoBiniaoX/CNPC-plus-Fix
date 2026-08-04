package top.cnpcplus.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class MixinContainerCarpentryBenchFix {

    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void cnpcplus$ensureResult(Container container, CallbackInfo ci) {
        if (!(((Object)this) instanceof ContainerCarpentryBench bench)) return;

        RecipeCarpentry recipe = RecipeController.instance.findMatchingRecipe(bench.craftMatrix);
        if (recipe == null) {
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
