package bin.cnpcplus.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.services.RecipeServices;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import noppes.npcs.controllers.RecipeController;

public final class RecipeGlobalBootstrap {
    private static boolean injected;

    private RecipeGlobalBootstrap() {}

    public static void onServerStarting(MinecraftServer server) {
        injected = false;
        // Controller may not have loaded recipes yet; ServerTick will retry
        reinject("ServerStarting");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (RecipeController.instance == null) return;
        // Keep retrying until at least one successful inject after recipes are loaded,
        // or until maps are empty (nothing to inject).
        if (injected) return;
        if (RecipeController.instance.globalRecipes == null) return;
        if (RecipeController.instance.globalRecipes.isEmpty()) {
            // still mark done so we don't spin forever on empty worlds before load
            // but allow re-inject after loadAll by resetting injected in onServerStarting only
            return;
        }
        reinject("ServerTick");
        injected = true;
    }

    /** Call after loadAll/save so globals are re-injected even mid-session. */
    public static void forceReinject() {
        injected = false;
        reinject("Force");
        injected = true;
    }

    private static void reinject(String phase) {
        if (RecipeController.instance == null) {
            return;
        }
        try {
            RecipeServices.reloadGlobalIntoRecipeManager(RecipeController.instance);
        } catch (Throwable t) {
            CnpcPlus.LOGGER.error("[RecipeGlobalBootstrap] " + phase + " failed", t);
        }
    }
}
