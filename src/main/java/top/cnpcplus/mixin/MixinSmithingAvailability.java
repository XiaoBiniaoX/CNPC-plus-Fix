package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.cnpcplus.craftingview.RecipeAvailability;
import top.cnpcplus.smithing.CustomSmithingRecipe;

@Mixin(SmithingMenu.class)
public abstract class MixinSmithingAvailability extends ItemCombinerMenu {
    @Shadow private SmithingRecipe selectedRecipe;
    protected MixinSmithingAvailability(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) { super(type, id, inventory, access); }
    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$result(CallbackInfo ci) {
        var matches = player.level().getRecipeManager().getRecipesFor(RecipeType.SMITHING, inputSlots, player.level());
        selectedRecipe = null;
        resultSlots.setItem(0, ItemStack.EMPTY);
        for (SmithingRecipe recipe : matches) {
            if (recipe instanceof CustomSmithingRecipe custom && !RecipeAvailability.allows(custom.availability, player)) continue;
            selectedRecipe = recipe;
            resultSlots.setRecipeUsed(recipe);
            resultSlots.setItem(0, recipe.assemble(inputSlots, player.level().registryAccess()));
            break;
        }
        ci.cancel();
    }
    @Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$pickup(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
        if (selectedRecipe instanceof CustomSmithingRecipe custom && !RecipeAvailability.allows(custom.availability, player)) cir.setReturnValue(false);
    }
}
