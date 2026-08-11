package bin.cnpcplus.recipe;

import bin.cnpcplus.recipe.id.RecipeIds;
import bin.cnpcplus.recipe.runtime.RecipeRuntime;
import bin.cnpcplus.recipe.services.RecipeServices;
import bin.cnpcplus.recipe.storage.RecipeStorage;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.Server;
import noppes.npcs.constants.EnumPacketClient;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

/**
 * Facade for RecipeController. Identity is recipe.id / CnpcPlusSyncId.
 */
public final class RecipeControllerFacade {
    private RecipeControllerFacade() {}

    public static void loadAll(RecipeController controller) {
        RecipeStorage.INSTANCE.loadAll(controller);
        reloadGlobalRecipes(controller);
        if (RecipeDebug.enabled()) {
            RecipeDebug.probeAllGlobals();
        }
    }

    public static void reloadGlobalRecipes(RecipeController controller) {
        RecipeServices.reloadGlobalIntoRecipeManager(controller);
    }

    public static RecipeCarpentry saveRecipe(RecipeCarpentry recipe, RecipeController controller) {
        return saveRecipe(recipe, controller, -1);
    }

    public static RecipeCarpentry saveRecipe(RecipeCarpentry recipe, RecipeController controller, int preferredSyncId) {
        if (recipe == null) return null;
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = "unnamed";
        }

        boolean emptyDraft = (recipe.getResult() == null || recipe.getResult().isEmpty())
                && (recipe.getIngredients() == null || recipe.getIngredients().isEmpty());
        if (emptyDraft && preferredSyncId <= 0 && recipe.id <= 0) {
            return recipe;
        }

        Integer syncId = preferredSyncId > 0 ? Integer.valueOf(preferredSyncId) : null;
        if (syncId == null && recipe.id > 0) {
            syncId = Integer.valueOf(recipe.id);
        }
        if (syncId == null) {
            syncId = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        }

        if (syncId != null && RecipeIds.INSTANCE.bySyncId(syncId.intValue()) != null) {
            controller.globalRecipes.remove(syncId);
            controller.anvilRecipes.remove(syncId);
            String base = recipe.name;
            int n = 0;
            while (nameTakenByOther(recipe.name, syncId.intValue())) {
                n++;
                recipe.name = base + "_" + n;
            }
            RecipeIds.INSTANCE.unregister(syncId.intValue());
            RecipeIds.INSTANCE.register(recipe, syncId.intValue());
        } else {
            String base = recipe.name;
            int n = 0;
            while (RecipeIds.INSTANCE.syncIdByName(recipe.name) != null) {
                n++;
                recipe.name = base + "_" + n;
            }
            if (syncId != null && syncId.intValue() > 0) {
                RecipeIds.INSTANCE.register(recipe, syncId.intValue());
            } else {
                RecipeIds.INSTANCE.register(recipe);
            }
            syncId = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        }

        int id = syncId != null ? syncId.intValue() : recipe.id;
        recipe.id = id;
        controller.globalRecipes.remove(Integer.valueOf(id));
        controller.anvilRecipes.remove(Integer.valueOf(id));
        purgeNameFromMaps(controller, recipe.name, id);

        if (recipe.isGlobal) {
            controller.globalRecipes.put(Integer.valueOf(id), recipe);
        } else {
            controller.anvilRecipes.put(Integer.valueOf(id), recipe);
        }

        RecipeStorage.INSTANCE.saveAll(controller);
        reloadGlobalRecipes(controller);

        MinecraftServer server = CustomNpcs.Server;
        if (server != null) {
            if (recipe.isGlobal) {
                Server.sendToAll(server, EnumPacketClient.SYNC_UPDATE, new Object[]{Integer.valueOf(6), recipe.writeNBT()});
            } else {
                Server.sendToAll(server, EnumPacketClient.SYNC_UPDATE, new Object[]{Integer.valueOf(7), recipe.writeNBT()});
            }
        }

        return recipe;
    }

    private static boolean nameTakenByOther(String name, int selfSyncId) {
        Integer id = RecipeIds.INSTANCE.syncIdByName(name);
        return id != null && id.intValue() != selfSyncId;
    }

    private static void purgeNameFromMaps(RecipeController controller, String name, int keepId) {
        if (name == null) return;
        java.util.Iterator<java.util.Map.Entry<Integer, RecipeCarpentry>> it;
        it = controller.globalRecipes.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, RecipeCarpentry> e = it.next();
            if (e.getValue() != null && name.equals(e.getValue().name) && e.getKey().intValue() != keepId) {
                it.remove();
            }
        }
        it = controller.anvilRecipes.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, RecipeCarpentry> e = it.next();
            if (e.getValue() != null && name.equals(e.getValue().name) && e.getKey().intValue() != keepId) {
                it.remove();
            }
        }
    }

    public static RecipeCarpentry delete(int syncId, RecipeController controller) {
        RecipeCarpentry recipe = RecipeIds.INSTANCE.bySyncId(syncId);
        if (recipe == null) {
            recipe = controller.getRecipe(syncId);
        }
        if (recipe == null) {
            return null;
        }
        controller.globalRecipes.remove(Integer.valueOf(syncId));
        controller.anvilRecipes.remove(Integer.valueOf(syncId));
        RecipeIds.INSTANCE.unregister(syncId);
        RecipeStorage.INSTANCE.saveAll(controller);
        reloadGlobalRecipes(controller);

        MinecraftServer server = CustomNpcs.Server;
        if (server != null) {
            if (recipe.isGlobal) {
                Server.sendToAll(server, EnumPacketClient.SYNC_REMOVE, new Object[]{Integer.valueOf(6), Integer.valueOf(syncId)});
            } else {
                Server.sendToAll(server, EnumPacketClient.SYNC_REMOVE, new Object[]{Integer.valueOf(7), Integer.valueOf(syncId)});
            }
        }
        recipe.id = -1;
        return recipe;
    }

    public static RecipeCarpentry getRecipe(int syncId) {
        RecipeCarpentry r = RecipeIds.INSTANCE.bySyncId(syncId);
        if (r != null) return r;
        if (RecipeController.instance == null) return null;
        return RecipeController.instance.globalRecipes.containsKey(Integer.valueOf(syncId))
                ? RecipeController.instance.globalRecipes.get(Integer.valueOf(syncId))
                : RecipeController.instance.anvilRecipes.get(Integer.valueOf(syncId));
    }

    public static RecipeCarpentry findMatchingRecipe(InventoryCrafting container) {
        return RecipeRuntime.INSTANCE.findMatchingRecipe(container);
    }
}
