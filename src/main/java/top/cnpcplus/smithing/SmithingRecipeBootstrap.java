package top.cnpcplus.smithing;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** 首次启动、数据包重载、玩家登录及关服的服务端权威链。 */
public final class SmithingRecipeBootstrap {
    private SmithingRecipeBootstrap() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SmithingRecipeManager.injectAll(event.getServer().getRecipeManager());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        if (event.getPlayer() == null) {
            SmithingRecipeManager.injectAll(server.getRecipeManager());
            for (var player : event.getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof SmithingMenuAccess menu) {
                    menu.cnpcplus$refreshRecipes();
                    player.containerMenu.broadcastChanges();
                }
            }
            SmithingRecipeManager.syncToPlayers(server);
        } else {
            SmithingRecipeManager.syncToPlayers(server, event.getPlayer());
        }
        // 原版在事件之后发送全量配方，无须重复群发。
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SmithingRecipeRegistry.awaitDiskPending();
        SmithingRecipeRegistry.clearCache();
    }
}
