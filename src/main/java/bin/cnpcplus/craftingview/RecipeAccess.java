package bin.cnpcplus.craftingview;

import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecipeAccess {
    private RecipeAccess() {}

    /** Sidebar shows only recipes whose availability the current player satisfies. */
    private static boolean isAvailable(RecipeCarpentry r) {
        try {
            Player player = Minecraft.getInstance().player;
            if (player == null || r.availability == null) return true;
            return r.availability.isAvailable(player);
        } catch (Throwable t) {
            return true;
        }
    }

    public static List<RecipeView> getAnvilRecipes() {
        List<RecipeView> list = new ArrayList<>();
        RecipeController c = RecipeController.instance;
        if (c == null || c.anvilRecipes == null) return list;
        for (Map.Entry<ResourceLocation, RecipeCarpentry> e : c.anvilRecipes.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null || r.getResult() == null || r.getResult().isEmpty()) continue;
            if (!isAvailable(r)) continue;
            RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) r;
            int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
            int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
            list.add(new RecipeView(e.getKey(), r, ox, oy));
        }
        return list;
    }

    public static List<RecipeView> getGlobalRecipes() {
        List<RecipeView> list = new ArrayList<>();
        RecipeController c = RecipeController.instance;
        if (c == null || c.globalRecipes == null) return list;
        for (Map.Entry<ResourceLocation, RecipeCarpentry> e : c.globalRecipes.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null || !r.isGlobal) continue;
            if (r.getResult() == null || r.getResult().isEmpty()) continue;
            if (!isAvailable(r)) continue;
            RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) r;
            int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
            int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
            list.add(new RecipeView(e.getKey(), r, ox, oy));
        }
        return list;
    }

    public static RecipeCarpentry getAnvil(ResourceLocation id) {
        RecipeController c = RecipeController.instance;
        if (c == null || c.anvilRecipes == null) return null;
        return c.anvilRecipes.get(id);
    }

    public static RecipeCarpentry getGlobal(ResourceLocation id) {
        RecipeController c = RecipeController.instance;
        if (c == null || c.globalRecipes == null) return null;
        return c.globalRecipes.get(id);
    }
}