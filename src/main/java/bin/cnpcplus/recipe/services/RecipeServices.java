package bin.cnpcplus.recipe.services;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.NoppesUtilPlayer;

/**
 * Platform services only (item keys, stack compare). No business rules dump.
 * Phase3: RecipeManager inject hooks live here.
 */
public final class RecipeServices {
    private RecipeServices() {}

    public static ResourceLocation itemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static boolean compareItems(ItemStack recipe, ItemStack actual, boolean ignoreDamage, boolean ignoreNBT) {
        return NoppesUtilPlayer.compareItems(recipe, actual, ignoreDamage, ignoreNBT);
    }
}
