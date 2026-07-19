package bin.cnpcplus.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.id.RecipeIds;
import bin.cnpcplus.recipe.runtime.RecipeRuntime;
import bin.cnpcplus.recipe.storage.RecipeStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

/**
 * Thin facade. Same display name replaces existing recipe (no 新建_1 spam).
 */
public final class RecipeControllerFacade {
    private RecipeControllerFacade() {}

    public static void loadAll(net.minecraft.core.HolderLookup.Provider provider, RecipeController controller) {
        CnpcPlus.LOGGER.info("[RecipeControllerFacade] loadAll");
        RecipeStorage.INSTANCE.loadAll(provider, controller);
    }

    public static RecipeCarpentry saveRecipe(RecipeCarpentry recipe, RecipeController controller) {
        if (recipe == null) return null;
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = "unnamed";
        }

        // Prefer replace by name: packet always creates a NEW object instance
        Integer byName = RecipeIds.INSTANCE.syncIdByName(recipe.name);
        Integer byObj = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        Integer syncId = byObj != null ? byObj : byName;

        if (syncId != null) {
            ResourceLocation oldLoc = RecipeIds.INSTANCE.locationBySyncId(syncId);
            if (oldLoc != null) {
                controller.globalRecipes.remove(oldLoc);
                controller.anvilRecipes.remove(oldLoc);
            }
            RecipeIds.INSTANCE.unregister(syncId);
            // re-register under same sync id
            RecipeIds.INSTANCE.register(recipe, syncId);
        } else {
            RecipeIds.INSTANCE.register(recipe);
            syncId = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        }

        ResourceLocation loc = RecipeIds.INSTANCE.locationOf(syncId != null ? syncId : 0);
        if (recipe.isGlobal) {
            controller.globalRecipes.put(loc, recipe);
            controller.anvilRecipes.remove(loc);
        } else {
            controller.anvilRecipes.put(loc, recipe);
            controller.globalRecipes.remove(loc);
        }

        if (CustomNpcs.Server != null) {
            RecipeStorage.INSTANCE.saveAll(CustomNpcs.Server.registryAccess(), controller);
        }
        CnpcPlus.LOGGER.info("[RecipeControllerFacade] saved name={} syncId={} global={} ingredients={} resultEmpty={}",
                recipe.name, syncId, recipe.isGlobal,
                recipe.getIngredients() != null ? recipe.getIngredients().size() : -1,
                recipe.getResult() == null || recipe.getResult().isEmpty());
        return recipe;
    }

    public static RecipeCarpentry delete(int syncId, RecipeController controller) {
        RecipeCarpentry recipe = RecipeIds.INSTANCE.bySyncId(syncId);
        if (recipe == null) {
            return new RecipeCarpentry("");
        }
        ResourceLocation loc = RecipeIds.INSTANCE.locationBySyncId(syncId);
        if (loc != null) {
            controller.globalRecipes.remove(loc);
            controller.anvilRecipes.remove(loc);
        }
        RecipeIds.INSTANCE.unregister(syncId);
        if (CustomNpcs.Server != null) {
            RecipeStorage.INSTANCE.saveAll(CustomNpcs.Server.registryAccess(), controller);
        }
        CnpcPlus.LOGGER.info("[RecipeControllerFacade] deleted name={} id={}", recipe.name, syncId);
        return recipe;
    }

    public static RecipeCarpentry getRecipe(int syncId) {
        return RecipeIds.INSTANCE.bySyncId(syncId);
    }

    public static RecipeCarpentry findMatchingRecipe(CraftingContainer container) {
        RecipeCarpentry r = RecipeRuntime.INSTANCE.findMatchingRecipe(container);
        CnpcPlus.LOGGER.debug("[RecipeControllerFacade] findMatching hit={}", r != null ? r.name : null);
        return r;
    }
}