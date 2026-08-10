package bin.cnpcplus.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.services.RecipeServices;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import noppes.npcs.controllers.RecipeController;

/**
 * Re-inject global 3x3 recipes after server is up (datapacks already loaded).
 * Avoids fragile RecipeManager.apply mixin remap.
 */
public final class RecipeGlobalBootstrap {
    private RecipeGlobalBootstrap() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // RecipeController may load during server start; try inject early
        reinject("ServerStarting");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        reinject("ServerStarted");
    }

    private static void reinject(String phase) {
        if (RecipeController.instance == null) {
            return;
        }
        try {
            RecipeServices.reloadGlobalIntoRecipeManager(RecipeController.instance);
        } catch (Throwable t) {
            CnpcPlus.LOGGER.error("[RecipeGlobalBootstrap] {} failed", phase, t);
        }
    }
}