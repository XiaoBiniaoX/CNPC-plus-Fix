package bin.cnpcplus.playerdata;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 玩家登出时从所有 NPC 的 tracking 集合移除其条目，防止实体 ID 复用导致 NPC 隐形。
 *
 * <p>CNPC tracking 以实体 ID 为键。玩家退出后 ID 残留，新玩家命中旧条目时
 * setVisible 跳过首次可见性检查 → NPC 对该玩家隐形。登出时主动清理。
 */
public final class PlayerTrackingCleanup {

    private PlayerTrackingCleanup() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;
        int id = player.getId();
        double r = 3.0E7;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            AABB box = new AABB(-r, level.getMinBuildHeight(), -r, r, level.getMaxBuildHeight(), r);
            for (EntityNPCInterface npc : level.getEntitiesOfClass(EntityNPCInterface.class, box, e -> true)) {
                npc.tracking.remove(id);
            }
        }
    }
}
