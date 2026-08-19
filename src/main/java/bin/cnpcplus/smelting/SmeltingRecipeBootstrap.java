package bin.cnpcplus.smelting;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.services.RecipeServices;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** 负责熔炼源数据在服务器生命周期中的读取、注入和缓存清理。 */
public final class SmeltingRecipeBootstrap {
    private SmeltingRecipeBootstrap() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        reload(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        reload(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SmeltingRecipeRegistry.clear();
    }

    private static void reload(MinecraftServer server) {
        try {
            RecipeServices.reloadSmeltingRecipes(server);
        } catch (Throwable error) {
            CnpcPlus.LOGGER.error("自定义熔炼配方启动注入失败", error);
        }
    }
}
