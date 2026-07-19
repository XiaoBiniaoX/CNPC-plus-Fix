package bin.cnpcplus.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.id.RecipeIds;
import bin.cnpcplus.recipe.runtime.RecipeRuntime;
import bin.cnpcplus.recipe.services.RecipeServices;
import bin.cnpcplus.recipe.storage.RecipeStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

/**
 * Thin facade. Identity is CnpcPlusSyncId (stable across renames), not display name.
 */
public final class RecipeControllerFacade {
    private RecipeControllerFacade() {}

    public static void loadAll(net.minecraft.core.HolderLookup.Provider provider, RecipeController controller) {
        CnpcPlus.LOGGER.info("[RecipeControllerFacade] loadAll");
        RecipeStorage.INSTANCE.loadAll(provider, controller);
        reloadGlobalRecipes(controller);
        
        if (RecipeDebug.enabled()) {
            RecipeDebug.probeAllGlobals();
        }
    }

    public static void reloadGlobalRecipes(RecipeController controller) {
        CnpcPlus.LOGGER.info("[RecipeControllerFacade] reloadGlobalRecipes count={}",
                controller != null && controller.globalRecipes != null ? controller.globalRecipes.size() : 0);
        RecipeServices.reloadGlobalIntoRecipeManager(controller);
    }

    /**
     * Called from packet after load(nbt). Optional sync id may be in lastNbt if set by mixin.
     */
    public static RecipeCarpentry saveRecipe(RecipeCarpentry recipe, RecipeController controller) {
        return saveRecipe(recipe, controller, -1);
    }

    public static RecipeCarpentry saveRecipe(RecipeCarpentry recipe, RecipeController controller, int preferredSyncId) {
        if (recipe == null) return null;
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = "unnamed";
        }
        // Skip pure empty draft creates (no result, no ingredients) unless updating existing id
        boolean emptyDraft = (recipe.getResult() == null || recipe.getResult().isEmpty())
                && (recipe.getIngredients() == null || recipe.getIngredients().isEmpty());
        if (emptyDraft && preferredSyncId <= 0) {
            CnpcPlus.LOGGER.info("[RecipeControllerFacade] skip empty draft name={}", recipe.name);
            return recipe;
        }

        // Resolve identity: preferred sync id > same object > exact name only if not a rename conflict
        Integer syncId = preferredSyncId > 0 ? preferredSyncId : null;
        if (syncId == null) {
            syncId = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        }
        // Do NOT use byName(newName) as primary identity 闁?rename would create a second recipe
        // and leave the old one orphaned or appear "replaced" when list refreshes incorrectly.

        if (syncId != null && RecipeIds.INSTANCE.bySyncId(syncId) != null) {
            // UPDATE existing: drop old map keys, rebind same sync id (name may change)
            ResourceLocation oldLoc = RecipeIds.INSTANCE.locationBySyncId(syncId);
            if (oldLoc != null) {
                controller.globalRecipes.remove(oldLoc);
                controller.anvilRecipes.remove(oldLoc);
            }
            // If another recipe already has this display name, suffix (except ourselves)
            String base = recipe.name;
            int n = 0;
            while (nameTakenByOther(recipe.name, syncId)) {
                n++;
                recipe.name = base + "_" + n;
            }
            RecipeIds.INSTANCE.unregister(syncId);
            RecipeIds.INSTANCE.register(recipe, syncId);
        } else {
            // CREATE new
            String base = recipe.name;
            int n = 0;
            while (RecipeIds.INSTANCE.syncIdByName(recipe.name) != null) {
                n++;
                recipe.name = base + "_" + n;
            }
            RecipeIds.INSTANCE.register(recipe);
            syncId = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        }

        ResourceLocation loc = RecipeIds.INSTANCE.locationOf(syncId != null ? syncId : 0);
        controller.globalRecipes.remove(loc);
        controller.anvilRecipes.remove(loc);
        // also purge any stale entries with same display name in the other map
        purgeNameFromMaps(controller, recipe.name, loc);

        if (recipe.isGlobal) {
            controller.globalRecipes.put(loc, recipe);
        } else {
            controller.anvilRecipes.put(loc, recipe);
        }

        if (CustomNpcs.Server != null) {
            RecipeStorage.INSTANCE.saveAll(CustomNpcs.Server.registryAccess(), controller);
        }
        reloadGlobalRecipes(controller);
        

        CnpcPlus.LOGGER.info("[RecipeControllerFacade] saved name={} syncId={} global={} ings={} resultEmpty={}",
                recipe.name, syncId, recipe.isGlobal,
                recipe.getIngredients() != null ? recipe.getIngredients().size() : -1,
                recipe.getResult() == null || recipe.getResult().isEmpty());

        if (RecipeDebug.enabled() && recipe.isGlobal) {
            ResourceLocation injectId = RecipeDebug.injectIdOf(loc);
            RecipeDebug.probeRecipe(recipe, injectId);
        }
        return recipe;
    }

    private static boolean nameTakenByOther(String name, int selfSyncId) {
        Integer id = RecipeIds.INSTANCE.syncIdByName(name);
        return id != null && id != selfSyncId;
    }

    private static void purgeNameFromMaps(RecipeController controller, String name, ResourceLocation keepLoc) {
        if (name == null) return;
        controller.globalRecipes.entrySet().removeIf(e ->
                e.getValue() != null && name.equals(e.getValue().name) && !e.getKey().equals(keepLoc));
        controller.anvilRecipes.entrySet().removeIf(e ->
                e.getValue() != null && name.equals(e.getValue().name) && !e.getKey().equals(keepLoc));
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
        reloadGlobalRecipes(controller);
        
        CnpcPlus.LOGGER.info("[RecipeControllerFacade] deleted name={} id={}", recipe.name, syncId);
        return recipe;
    }

    public static RecipeCarpentry getRecipe(int syncId) {
        return RecipeIds.INSTANCE.bySyncId(syncId);
    }

    public static RecipeCarpentry findMatchingRecipe(CraftingContainer container) {
        return RecipeRuntime.INSTANCE.findMatchingRecipe(container);
    }
}