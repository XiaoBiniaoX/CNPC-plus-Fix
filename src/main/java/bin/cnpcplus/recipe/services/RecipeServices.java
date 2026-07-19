package bin.cnpcplus.recipe.services;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.CraftUtils;
import bin.cnpcplus.recipe.RecipeDebug;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            CnpcPlus.LOGGER.debug("[RecipeServices] reloadGlobal skip: no server");
            return;
        }
        if (controller == null) return;

        RecipeManager manager = server.getRecipeManager();
        if (manager == null) return;

        int managerHash = System.identityHashCode(manager);
        List<RecipeHolder<?>> next = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            ResourceLocation id = holder.id();
            if (id != null && "customnpcs".equals(id.getNamespace()) && id.getPath().startsWith("global/")) {
                continue;
            }
            next.add(holder);
        }

        List<ResourceLocation> injectedIds = new ArrayList<>();
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
                injectedIds.add(id);
                added++;

                if (RecipeDebug.enabled()) {
                    Object type = null;
                    Object category = null;
                    boolean can3 = false;
                    try { type = recipe.getType(); } catch (Throwable ignored) {}
                    try { category = recipe.category(); } catch (Throwable ignored) {}
                    try { can3 = recipe.canCraftInDimensions(3, 3); } catch (Throwable ignored) {}
                    RecipeDebug.info("inject id={} recipeClass={} getType()={} category()={} canCraft3x3={} ings={}",
                            id, recipe.getClass().getName(), type, category, can3, recipe.getIngredients().size());
                }
            }
        }

        try {
            manager.replaceRecipes(next);
            CnpcPlus.LOGGER.info("[RecipeServices] reloadGlobal into RecipeManager: added={} total={} managerHash={}",
                    added, next.size(), managerHash);

            if (RecipeDebug.enabled()) {
                int afterHash = System.identityHashCode(server.getRecipeManager());
                int craftingSize = 0;
                try {
                    craftingSize = manager.getAllRecipesFor(RecipeType.CRAFTING).size();
                } catch (Throwable t) {
                    RecipeDebug.info("getAllRecipesFor(CRAFTING) failed: {}", t.toString());
                }
                RecipeDebug.info("postInject managerHashBefore={} after={} craftingSize={}", managerHash, afterHash, craftingSize);
                for (ResourceLocation id : injectedIds) {
                    Optional<? extends RecipeHolder<?>> byKey = manager.byKey(id);
                    boolean inCrafting = false;
                    Object actualType = null;
                    try {
                        for (RecipeHolder<CraftingRecipe> h : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
                            if (id.equals(h.id())) {
                                inCrafting = true;
                                actualType = h.value().getType();
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                    if (byKey.isPresent() && actualType == null) {
                        try { actualType = byKey.get().value().getType(); } catch (Throwable ignored) {}
                    }
                    RecipeDebug.info("verify id={} byKey={} inCraftingList={} value.getType()={}",
                            id, byKey.isPresent(), inCrafting, actualType);
                }
            }
        } catch (Exception ex) {
            CnpcPlus.LOGGER.error("[RecipeServices] replaceRecipes failed", ex);
        }
    }
}