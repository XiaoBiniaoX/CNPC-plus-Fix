package bin.cnpcplus.mixin.smithing;

import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import top.cnpcplus.smithing.SmithingMenuAccess;

import java.util.List;

@Mixin(value = SmithingMenu.class, remap = false)
public abstract class MixinSmithingMenu extends net.minecraft.world.inventory.ItemCombinerMenu implements SmithingMenuAccess {
    protected MixinSmithingMenu(net.minecraft.world.inventory.MenuType<?> type, int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.inventory.ContainerLevelAccess access) { super(type, id, inventory, access); }
    @Shadow @Final private Level level;
    @Shadow @Final @Mutable private List<RecipeHolder<SmithingRecipe>> recipes;
    @Shadow private RecipeHolder<SmithingRecipe> selectedRecipe;
    @Shadow public abstract void createResult();

    @org.spongepowered.asm.mixin.injection.Inject(method = "createResult", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$availableResult(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var input = new net.minecraft.world.item.crafting.SmithingRecipeInput(inputSlots.getItem(0), inputSlots.getItem(1), inputSlots.getItem(2));
        selectedRecipe = null;
        resultSlots.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
        for (var holder : level.getRecipeManager().getRecipesFor(RecipeType.SMITHING, input, level)) {
            if (holder.value() instanceof top.cnpcplus.smithing.CustomSmithingRecipe recipe
                    && !top.cnpcplus.craftingview.RecipeAvailability.allows(recipe.availability, player)) continue;
            var output = holder.value().assemble(input, level.registryAccess());
            if (!output.isItemEnabled(level.enabledFeatures())) continue;
            selectedRecipe = holder;
            resultSlots.setRecipeUsed(holder);
            resultSlots.setItem(0, output);
            break;
        }
        ci.cancel();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "mayPickup", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$availablePickup(net.minecraft.world.entity.player.Player player, boolean hasStack, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (selectedRecipe != null && selectedRecipe.value() instanceof top.cnpcplus.smithing.CustomSmithingRecipe recipe
                && !top.cnpcplus.craftingview.RecipeAvailability.allows(recipe.availability, player)) cir.setReturnValue(false);
    }

    @Override
    public void cnpcplus$refreshRecipes() {
        this.recipes = this.level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING);
        this.selectedRecipe = null;
        this.createResult();
    }
}
