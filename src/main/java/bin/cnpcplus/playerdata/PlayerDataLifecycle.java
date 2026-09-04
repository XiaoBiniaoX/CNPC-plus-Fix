package bin.cnpcplus.playerdata;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家退出服务器时落盘并移除其 PlayerData 缓存。
 *
 * <p>CNPC 1.21.1 从 Forge Capability 迁移到静态表后没有移植销毁逻辑，
 * 缓存条目会一直留在内存里。配合实体 ID 复用就会串数据（表现为进出后数据被重置），
 * 同时也是内存泄漏。这里在退出时清理，与 UUID 键改造配套。
 */
public final class PlayerDataLifecycle {

    private PlayerDataLifecycle() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;
        PlayerDataStore.unload(player);
    }
}
