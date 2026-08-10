package bin.cnpcplus.recipe.runtime;

import bin.cnpcplus.recipe.MatchResult;
import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import bin.cnpcplus.recipe.services.RecipeServices;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import noppes.npcs.controllers.data.RecipeCarpentry;

/**
 * Matching for workbench InventoryCrafting (typically 3x3).
 */
public final class CraftingInputMatcher {
    private CraftingInputMatcher() {}

    public static boolean matches(RecipeCarpentry recipe, InventoryCrafting input) {
        return match(recipe, input).hit();
    }

    public static MatchResult match(RecipeCarpentry recipe, InventoryCrafting input) {
        if (recipe == null || input == null) return MatchResult.MISS;
        if (recipe.getResult() == null || recipe.getResult().isEmpty()) return MatchResult.MISS;
        NonNullList<Ingredient> ings = recipe.getIngredients();
        if (ings == null || ings.isEmpty()) return MatchResult.MISS;

        int gridW = Math.max(1, input.getWidth());
        int gridH = Math.max(1, input.getHeight());
        int rw = Math.max(1, recipe.getRecipeWidth());
        int rh = Math.max(1, recipe.getRecipeHeight());
        if (rw > gridW || rh > gridH) return MatchResult.MISS;
        // ShapedRecipes width/height getters

        RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) recipe;
        if (off.cnpcplusHasSavedOffset()) {
            int ox = off.cnpcplusGetOffsetX();
            int oy = off.cnpcplusGetOffsetY();
            if (ox + rw <= gridW && oy + rh <= gridH) {
                if (check(recipe, input, gridW, gridH, ox, oy, false)) {
                    return new MatchResult(recipe, ox, oy, false, 2);
                }
                if (check(recipe, input, gridW, gridH, ox, oy, true)) {
                    return new MatchResult(recipe, ox, oy, true, 2);
                }
            }
        }

        for (int ox = 0; ox <= gridW - rw; ox++) {
            for (int oy = 0; oy <= gridH - rh; oy++) {
                if (check(recipe, input, gridW, gridH, ox, oy, false)) {
                    return new MatchResult(recipe, ox, oy, false, 1);
                }
                if (check(recipe, input, gridW, gridH, ox, oy, true)) {
                    return new MatchResult(recipe, ox, oy, true, 1);
                }
            }
        }
        return MatchResult.MISS;
    }

    private static boolean check(RecipeCarpentry recipe, InventoryCrafting input,
                                 int gridW, int gridH, int ox, int oy, boolean mirror) {
        int rw = Math.max(1, recipe.getRecipeWidth());
        int rh = Math.max(1, recipe.getRecipeHeight());
        NonNullList<Ingredient> ings = recipe.getIngredients();
        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                int ix = x - ox;
                int iy = y - oy;
                Ingredient ingredient = Ingredient.EMPTY;
                if (ix >= 0 && iy >= 0 && ix < rw && iy < rh) {
                    int idx = mirror ? (rw - ix - 1) + iy * rw : ix + iy * rw;
                    if (idx >= 0 && idx < ings.size()) {
                        ingredient = ings.get(idx);
                    }
                }
                ItemStack actual = input.getStackInRowAndColumn(x, y);
                boolean expectEmpty = ingredient == null || ingredient == Ingredient.EMPTY
                        || ingredient.getMatchingStacks().length == 0;
                if (expectEmpty) {
                    if (actual != null && !actual.isEmpty()) return false;
                    continue;
                }
                if (actual == null || actual.isEmpty()) return false;
                if (ingredient.apply(actual)) continue;
                ItemStack expected = first(ingredient);
                if (expected.isEmpty() || !RecipeServices.compareItems(expected, actual, recipe.ignoreDamage, recipe.ignoreNBT)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ItemStack first(Ingredient ingredient) {
        if (ingredient == null) return ItemStack.EMPTY;
        ItemStack[] items = ingredient.getMatchingStacks();
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }
}
