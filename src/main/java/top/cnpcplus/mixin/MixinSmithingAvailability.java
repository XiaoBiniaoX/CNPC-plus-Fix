package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.craftingview.RecipeAvailability;
import top.cnpcplus.smithing.CustomSmithingRecipe;
import java.util.List;

@Mixin(SmithingMenu.class)
public abstract class MixinSmithingAvailability extends ItemCombinerMenu {
    @Shadow private SmithingRecipe selectedRecipe;
    protected MixinSmithingAvailability(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) { super(type, id, inventory, access); }
    @ModifyVariable(method = "createResult", at = @At("STORE"), ordinal = 0, require = 1)
    private List<SmithingRecipe> cnpcplus$availableRecipes(List<SmithingRecipe> matches) {
        return matches.stream().filter(recipe -> !(recipe instanceof CustomSmithingRecipe custom)
                || RecipeAvailability.allows(custom.availability, player)).toList();
    }
    @Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$pickup(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
        if (selectedRecipe instanceof CustomSmithingRecipe custom && !RecipeAvailability.allows(custom.availability, player)) cir.setReturnValue(false);
    }
}
