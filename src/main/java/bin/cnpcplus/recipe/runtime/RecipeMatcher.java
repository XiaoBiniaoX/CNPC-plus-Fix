package bin.cnpcplus.recipe.runtime;

import bin.cnpcplus.recipe.MatchResult;
import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import bin.cnpcplus.recipe.services.RecipeServices;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.Collection;

/**
 * Pure matching against CraftingContainer grid (not trimmed CraftingInput).
 */
public final class RecipeMatcher {
    private RecipeMatcher() {}

    public static MatchResult findMatching(CraftingContainer inv, Collection<RecipeCarpentry> candidates) {
        if (inv == null || candidates == null || candidates.isEmpty()) return MatchResult.MISS;
        int gridW = Math.max(1, inv.getWidth());
        int gridH = Math.max(1, inv.getHeight());

        for (RecipeCarpentry recipe : candidates) {
            if (recipe == null) continue;
            if (recipe.getResult() == null || recipe.getResult().isEmpty()) continue;
            var ings = recipe.getIngredients();
            if (ings == null || ings.isEmpty()) continue;

            MatchResult hit = matchOne(recipe, inv, gridW, gridH);
            if (hit.hit()) {
                return hit;
            }
        }
        return MatchResult.MISS;
    }

    private static MatchResult matchOne(RecipeCarpentry recipe, CraftingContainer inv, int gridW, int gridH) {
        int rw = Math.max(1, recipe.getWidth());
        int rh = Math.max(1, recipe.getHeight());

        RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) recipe;
        if (off.cnpcplusHasSavedOffset()) {
            int ox = off.cnpcplusGetOffsetX();
            int oy = off.cnpcplusGetOffsetY();
            if (checkMatch(recipe, inv, gridW, gridH, ox, oy, false)) {
                return new MatchResult(recipe, ox, oy, false, 2);
            }
            if (checkMatch(recipe, inv, gridW, gridH, ox, oy, true)) {
                return new MatchResult(recipe, ox, oy, true, 2);
            }
        }

        for (int ox = 0; ox <= gridW - rw; ox++) {
            for (int oy = 0; oy <= gridH - rh; oy++) {
                if (checkMatch(recipe, inv, gridW, gridH, ox, oy, false)) {
                    return new MatchResult(recipe, ox, oy, false, 1);
                }
                if (checkMatch(recipe, inv, gridW, gridH, ox, oy, true)) {
                    return new MatchResult(recipe, ox, oy, true, 1);
                }
            }
        }
        return MatchResult.MISS;
    }

    private static boolean checkMatch(RecipeCarpentry recipe, CraftingContainer inv,
                                      int gridW, int gridH, int offsetX, int offsetY, boolean mirror) {
        int rw = Math.max(1, recipe.getWidth());
        int rh = Math.max(1, recipe.getHeight());
        var ingredients = recipe.getIngredients();

        for (int y = 0; y < gridH; y++) {
            for (int x = 0; x < gridW; x++) {
                int ix = x - offsetX;
                int iy = y - offsetY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (ix >= 0 && iy >= 0 && ix < rw && iy < rh) {
                    int idx = mirror ? (rw - ix - 1) + iy * rw : ix + iy * rw;
                    if (idx >= 0 && idx < ingredients.size()) {
                        ingredient = ingredients.get(idx);
                    }
                }
                ItemStack actual = inv.getItem(x + y * gridW);
                boolean expectEmpty = ingredient == null || ingredient.isEmpty();
                if (expectEmpty) {
                    if (!actual.isEmpty()) return false;
                    continue;
                }
                if (actual.isEmpty()) return false;
                if (ingredient.test(actual)) continue;
                ItemStack expected = firstItem(ingredient);
                if (expected.isEmpty() || !RecipeServices.compareItems(expected, actual, recipe.ignoreDamage, recipe.ignoreNBT)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ItemStack firstItem(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return ItemStack.EMPTY;
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0];
    }
}