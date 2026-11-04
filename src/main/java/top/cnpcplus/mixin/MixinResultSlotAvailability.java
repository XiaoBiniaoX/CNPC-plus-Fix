package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.*;
import top.cnpcplus.craftingview.RecipeAvailability;

/** Checked before normal clicks and before quick-move transfers, not after output is awarded. */
@Mixin(ResultSlot.class)
public abstract class MixinResultSlotAvailability extends Slot {
    @Shadow @Final private CraftingContainer craftSlots;
    protected MixinResultSlotAvailability(net.minecraft.world.Container container, int index, int x, int y) { super(container, index, x, y); }
    @Override public boolean mayPickup(Player player) {
        RecipeCarpentry recipe;
        if (craftSlots.getContainerSize() == 16) recipe = RecipeController.instance.findMatchingRecipe(craftSlots);
        else {
            var found = player.level().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, craftSlots, player.level()).orElse(null);
            recipe = found instanceof RecipeCarpentry custom ? custom : null;
        }
        return recipe == null || RecipeAvailability.allows(recipe.availability, player);
    }
}
