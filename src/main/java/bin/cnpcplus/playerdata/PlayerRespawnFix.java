package bin.cnpcplus.playerdata;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家死亡复活时保持 CNPC 数据连续性。
 *
 * <p>死亡复活会产生新的 ServerPlayer 实体（新实体 ID），CNPC 原版的 dataMap 以实体 ID 为键，
 * 复活后缓存未命中会从磁盘重读，而磁盘文件可能落后于内存（异步保存竞态），导致任务/传送进度回退。
 *
 * <p>PlayerDataStore 已改为 UUID 键控，正常情况下缓存天然命中。
 * 本类作为兜底：在 Clone 事件中确保旧实体的数据已落盘，防止极端时序下读到旧文件。
 */
public final class PlayerRespawnFix {

    private PlayerRespawnFix() {
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide) return;
        if (!event.isWasDeath()) return;

        ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();
        // 复活前同步落盘，确保磁盘文件与内存一致（防止异步保存未完成时读到旧文件）
        try {
            PlayerDataStore.get(oldPlayer).save(false);
        } catch (Exception ignored) {
        }
    }
}
