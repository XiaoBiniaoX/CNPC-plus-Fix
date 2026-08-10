package bin.cnpcplus.recipe.runtime;

import bin.cnpcplus.recipe.MatchResult;
import bin.cnpcplus.recipe.id.RecipeIds;
import net.minecraft.inventory.InventoryCrafting;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

public final class RecipeRuntime {
    public static final RecipeRuntime INSTANCE = new RecipeRuntime();

    private RecipeRuntime() {}

    public RecipeCarpentry getBySyncId(int syncId) {
        return RecipeIds.INSTANCE.bySyncId(syncId);
    }

    public MatchResult findMatchingAnvil(InventoryCrafting container) {
        if (container == null || RecipeController.instance == null) return MatchResult.MISS;
        return RecipeMatcher.findMatching(container, RecipeController.instance.anvilRecipes.values());
    }

    public RecipeCarpentry findMatchingRecipe(InventoryCrafting container) {
        MatchResult r = findMatchingAnvil(container);
        return r.hit() ? r.recipe : null;
    }
}
