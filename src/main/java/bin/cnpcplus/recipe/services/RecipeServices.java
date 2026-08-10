package bin.cnpcplus.recipe.services;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.CraftUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Platform services: item compare (1.20.1 fuzzy rules) + RecipeManager inject.
 */
public final class RecipeServices {
    private RecipeServices() {}

    public static ResourceLocation itemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /**
     * Replaces NoppesUtilPlayer.compareItems for recipe matching.
     * ignoreDamage / ignoreNBT keep CNPC recipe flags but use 1.20.1 cnpcplus semantics.
     */
    public static boolean compareItems(ItemStack required, ItemStack actual, boolean ignoreDamage, boolean ignoreNBT) {
        // CraftUtils expects (playerStack, required, ...)
        return CraftUtils.matches(actual, required, ignoreDamage, ignoreNBT);
    }

    public static void reloadGlobalIntoRecipeManager(RecipeController controller) {
        MinecraftServer server = CustomNpcs.Server;
        if (server == null) {
            return;
        }
        if (controller == null) return;

        RecipeManager manager = server.getRecipeManager();
        if (manager == null) return;

        List<RecipeHolder<?>> next = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            ResourceLocation id = holder.id();
            if (id != null && "customnpcs".equals(id.getNamespace()) && id.getPath().startsWith("global/")) {
                continue;
            }
            next.add(holder);
        }

        int added = 0;
        if (controller.globalRecipes != null) {
            for (Map.Entry<ResourceLocation, RecipeCarpentry> e : controller.globalRecipes.entrySet()) {
                RecipeCarpentry recipe = e.getValue();
                if (recipe == null || !recipe.isGlobal) continue;
                if (recipe.getResult() == null || recipe.getResult().isEmpty()) continue;
                if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) continue;
                String path = e.getKey() != null ? e.getKey().getPath() : ("recipe_" + added);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("customnpcs", "global/" + path.replace(':', '_'));
                next.add(new RecipeHolder<>(id, recipe));
                added++;
            }
        }

        try {
            manager.replaceRecipes(next);
        } catch (Exception ex) {
            CnpcPlus.LOGGER.error("[RecipeServices] replaceRecipes failed", ex);
        }
    }
}