package bin.cnpcplus.craftingview;

import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecipeAccess {
    private RecipeAccess() {}

    public static List<RecipeView> getAnvilRecipes() {
        List<RecipeView> list = new ArrayList<RecipeView>();
        RecipeController c = RecipeController.instance;
        if (c == null || c.anvilRecipes == null) return list;
        for (Map.Entry<Integer, RecipeCarpentry> e : c.anvilRecipes.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null || r.getResult() == null || r.getResult().isEmpty()) continue;
            RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) r;
            int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
            int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
            list.add(new RecipeView(e.getKey().intValue(), r, ox, oy));
        }
        return list;
    }

    public static List<RecipeView> getGlobalRecipes() {
        List<RecipeView> list = new ArrayList<RecipeView>();
        RecipeController c = RecipeController.instance;
        if (c == null || c.globalRecipes == null) return list;
        for (Map.Entry<Integer, RecipeCarpentry> e : c.globalRecipes.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null || !r.isGlobal) continue;
            if (r.getResult() == null || r.getResult().isEmpty()) continue;
            RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) r;
            int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
            int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
            list.add(new RecipeView(e.getKey().intValue(), r, ox, oy));
        }
        return list;
    }

    public static RecipeCarpentry getAnvil(int id) {
        RecipeController c = RecipeController.instance;
        if (c == null || c.anvilRecipes == null) return null;
        return c.anvilRecipes.get(Integer.valueOf(id));
    }

    public static RecipeCarpentry getGlobal(int id) {
        RecipeController c = RecipeController.instance;
        if (c == null || c.globalRecipes == null) return null;
        return c.globalRecipes.get(Integer.valueOf(id));
    }
}
