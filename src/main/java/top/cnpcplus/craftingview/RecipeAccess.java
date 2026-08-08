package top.cnpcplus.craftingview;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;

public class RecipeAccess {
    private RecipeAccess() {}

    /**
     * 获取指定来源 map 的所有配方（过滤掉未满足对话/任务条件的配方）
     * @param global true → globalRecipes (工作台), false → anvilRecipes (木工台)
     */
    public static List<RecipeView> getRecipes(boolean global) {
        List<RecipeView> out = new ArrayList<>();
        var ctrl = RecipeController.instance;
        if (ctrl == null) return out;
        Player player = Minecraft.getInstance().player;
        var map = global ? ctrl.globalRecipes : ctrl.anvilRecipes;
        if (map != null) {
            for (var entry : map.entrySet()) {
                RecipeCarpentry recipe = entry.getValue();
                if (recipe == null) continue;
                if (player != null && recipe.availability.hasOptions() && !recipe.availability.isAvailable(player)) continue;
                out.add(new RecipeView(recipe, entry.getKey(), global));
            }
        }
        return out;
    }

    /** 在两个 map 中查找配方（不限来源） */
    public static RecipeView getRecipeById(ResourceLocation id) {
        var r = getRecipeById(id, true);
        return r != null ? r : getRecipeById(id, false);
    }

    /** 在指定来源 map 中查找配方 */
    public static RecipeView getRecipeById(ResourceLocation id, boolean global) {
        var ctrl = RecipeController.instance;
        if (ctrl == null) return null;
        var map = global ? ctrl.globalRecipes : ctrl.anvilRecipes;
        var r = map != null ? map.get(id) : null;
        return r != null ? new RecipeView(r, id, global) : null;
    }

    /** 判断配方在指定来源 map 中是否存在 */
    public static boolean recipeExists(ResourceLocation id, boolean global) {
        var ctrl = RecipeController.instance;
        if (ctrl == null) return false;
        var map = global ? ctrl.globalRecipes : ctrl.anvilRecipes;
        return map != null && map.containsKey(id);
    }
}
