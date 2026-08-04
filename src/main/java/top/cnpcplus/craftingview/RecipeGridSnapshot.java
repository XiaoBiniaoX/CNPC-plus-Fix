package top.cnpcplus.craftingview;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerManageRecipes;
import top.cnpcplus.mixin.ContainerManageRecipesAccess;

public final class RecipeGridSnapshot {
    private static ItemStack[] savedGrid;
    private static ResourceLocation savedRecipeId;

    private RecipeGridSnapshot() {}

    public static void save(ResourceLocation recipeId, ItemStack[] snapshot) {
        savedRecipeId = recipeId;
        savedGrid = snapshot;
    }

    public static boolean tryRestore(ContainerManageRecipes container, ResourceLocation recipeId) {
        if (savedGrid == null || savedRecipeId == null) return false;
        if (!savedRecipeId.equals(recipeId)) return false;

        SimpleContainer matrix = ((ContainerManageRecipesAccess) container).cnpcplus$getCraftingMatrix();
        for (int i = 0; i < savedGrid.length; i++) {
            matrix.setItem(i + 1, savedGrid[i].copy());
        }
        savedGrid = null;
        savedRecipeId = null;
        return true;
    }
}
