package bin.cnpcplus.playerdata;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import noppes.npcs.controllers.data.PlayerData;

/**
 * 定时异步保存在线玩家的 CNPC 数据（任务/传送/对话等）。
 *
 * <p>CNPC 原版只在死亡、登出、停服时保存，崩溃会丢失全部进度。
 * 本类按配置间隔（默认5分钟）触发一次全量保存，快照在主线程、IO由 CNPC 调度器异步执行。
 */
public final class PlayerAutoSave {

    private static int tickCounter = 0;

    private PlayerAutoSave() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int seconds = CnpcPlusConfig.PLAYER_AUTOSAVE_SECONDS.get();
        if (seconds <= 0) return;

        tickCounter++;
        if (tickCounter < seconds * 20) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                PlayerData.get(player).save(false);
            } catch (Exception e) {
                CnpcPlus.LOGGER.warn("自动保存玩家 {} 的 CNPC 数据失败", player.getGameProfile().getName(), e);
            }
        }
    }
}
