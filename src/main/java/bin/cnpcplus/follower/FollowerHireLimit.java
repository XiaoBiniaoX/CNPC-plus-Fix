package bin.cnpcplus.follower;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.common.IRoleFollowerOwnerAccess;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleFollower;

/**
 * 238-10：雇佣随从上限，按所有已加载维度的主人 UUID 统计。
 * 与原版 CNPC 一致，只拦截「新雇佣」，不影响续费/解散。
 */
public final class FollowerHireLimit {

    private FollowerHireLimit() {
    }

    public static boolean blockIfAtLimit(RoleFollower role, EntityPlayerMP player) {
        if (role == null || role.hasOwner() || player == null || player.getServer() == null) return false;
        int max = CnpcPlusConfig.getMaxHiredNpcs();
        if (max <= 0) return false;

        int count = 0;
        String owner = player.getUniqueID().toString();
        // ponytail: 与 1.20.1 同样只统计已加载实体；若需离线总额需独立持久化名册。
        for (WorldServer world : player.getServer().worlds) {
            for (Entity entity : world.loadedEntityList) {
                if (!(entity instanceof EntityNPCInterface) || entity.isDead) continue;
                EntityNPCInterface npc = (EntityNPCInterface) entity;
                if (!(npc.roleInterface instanceof RoleFollower)) continue;
                RoleFollower follower = (RoleFollower) npc.roleInterface;
                if (follower.hasOwner() && follower.getDays() > 0
                        && owner.equals(((IRoleFollowerOwnerAccess) follower).cnpcplus$getOwnerUUID())) count++;
            }
        }
        if (count < max) return false;

        String message = CnpcPlusConfig.getHireLimitMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "你雇佣的人过多";
        }
        player.sendMessage(new TextComponentString(message));
        return true;
    }
}
