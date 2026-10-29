package top.cnpcplus.transport.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashSet;
import java.util.Set;

/**
 * 任务241n：按 id 找传送师 NPC。
 * 之前用 `getEntitiesOfClass(..., 玩家包围盒 inflate 16)` → **NPC 离玩家稍远就找不到**
 * → 部分传送师 NPC 打开 UI 后没有任何数据（"无法打开UI"）。
 * 改为遍历该维度全部实体（1.20.1 ServerLevel#getAllEntities）。
 */
public final class TransportNpcLookup {
    private TransportNpcLookup() {}

    /** 与原生高级编辑包相同权限，并绑定服务端当前编辑对象。 */
    public static boolean canEdit(ServerPlayer player, EntityNPCInterface npc) {
        return npc != null && noppes.npcs.NoppesUtilServer.getEditingNpc(player) == npc
                && noppes.npcs.CustomNpcsPermissions.hasPermission(player, noppes.npcs.CustomNpcsPermissions.NPC_ADVANCED);
    }

    public static EntityNPCInterface find(ServerPlayer player, int npcId) {
        if (player == null || player.level() == null) return null;
        if (player.level() instanceof ServerLevel sl) {
            for (net.minecraft.world.entity.Entity e : sl.getAllEntities()) {
                if (e instanceof EntityNPCInterface n && n.getId() == npcId) return n;
            }
        }
        // 兜底：小范围搜索
        for (EntityNPCInterface e : player.level().getEntitiesOfClass(EntityNPCInterface.class,
                player.getBoundingBox().inflate(32.0, 32.0, 32.0))) {
            if (e.getId() == npcId) return e;
        }
        return null;
    }

    /**
     * 该服务器**所有维度**里活着的传送师 NPC 所占用的传送点 id。
     * 用于：①删除遗留条目前判断是否仍被占用 ②清理 NPC 后残留的孤儿点位。
     */
    public static Set<Integer> liveTransportIds(ServerPlayer player) {
        Set<Integer> ids = new HashSet<>();
        if (player == null || player.server == null) return ids;
        for (net.minecraft.server.level.ServerLevel lvl : player.server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity e : lvl.getAllEntities()) {
                if (!(e instanceof EntityNPCInterface n)) continue;
                if (n.role == null || n.role.getType() != 4) continue;
                noppes.npcs.roles.RoleTransporter role = (noppes.npcs.roles.RoleTransporter) n.role;
                noppes.npcs.controllers.data.TransportLocation loc = role.getLocation();
                if (loc != null) ids.add(loc.id);
            }
        }
        return ids;
    }
}
