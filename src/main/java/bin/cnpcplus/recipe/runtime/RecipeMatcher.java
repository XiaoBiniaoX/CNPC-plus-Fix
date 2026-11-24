package bin.cnpcplus.recipe.runtime;

import bin.cnpcplus.recipe.MatchResult;
import java.util.Collection;
import net.minecraft.inventory.InventoryCrafting;
import noppes.npcs.controllers.data.RecipeCarpentry;

public final class RecipeMatcher {
    private RecipeMatcher() {}
    public static MatchResult findMatching(InventoryCrafting inv, Collection<RecipeCarpentry> candidates) {
        if (inv == null || candidates == null) return MatchResult.MISS;
        for (RecipeCarpentry recipe : candidates) {
            MatchResult match = CraftingInputMatcher.match(recipe, inv);
            if (match.hit()) return match;
        }
        return MatchResult.MISS;
    }
}
