package top.cnpcplus.craftingview;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import top.cnpcplus.mixin.RecipeManagerAccess;

public final class RecipeIndexUpdates {
    private RecipeIndexUpdates() {}

    public static void replace(RecipeManager manager, RecipeType<?> type, ResourceLocation id, Recipe<?> recipe) {
        var access = (RecipeManagerAccess) manager;
        var current = access.cnpcplus$getRecipes();
        var group = current.getOrDefault(type, Map.of());
        if (recipe == null && !group.containsKey(id)) return;

        var updatedGroup = new LinkedHashMap<>(group);
        var byType = new HashMap<>(current);
        var byName = new HashMap<>(access.cnpcplus$getByName());
        if (recipe == null) {
            updatedGroup.remove(id);
            byName.remove(id);
        } else {
            updatedGroup.put(id, recipe);
            byName.put(id, recipe);
        }
        byType.put(type, updatedGroup);
        access.cnpcplus$setRecipes(byType);
        access.cnpcplus$setByName(byName);
    }
}
