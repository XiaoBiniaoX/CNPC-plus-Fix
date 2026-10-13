package bin.cnpcplus.smelting;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.services.RecipeServices;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/** 负责熔炼源数据在服务器生命周期中的读取、注入和缓存清理。 */
public final class SmeltingRecipeBootstrap {
    /** 保存/删除只打标记，主线程下一 tick 统一 reload，连续操作合并为一次全量重建。 */
    private static final AtomicBoolean RELOAD_PENDING = new AtomicBoolean();

    private SmeltingRecipeBootstrap() {}

    public static void markReloadPending() {
        RELOAD_PENDING.set(true);
    }

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
        RELOAD_PENDING.set(false);
        SmeltingRecipeRegistry.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!RELOAD_PENDING.compareAndSet(true, false)) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        reload(server);
    }

    private static void reload(MinecraftServer server) {
        try {
            RecipeServices.reloadSmeltingRecipes(server);
        } catch (Throwable error) {
            CnpcPlus.LOGGER.error("自定义熔炼配方启动注入失败", error);
        }
    }
}
