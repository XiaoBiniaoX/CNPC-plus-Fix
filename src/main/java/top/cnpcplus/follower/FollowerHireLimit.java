package top.cnpcplus.follower;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleFollower;
import top.cnpcplus.accessor.RoleFollowerOwnerAccess;
import bin.cnpcplus.config.CnpcPlusConfig;
import top.cnpcplus.config.ServerConfigAccess;

public final class FollowerHireLimit {
    private static final int DEFAULT_MAX_HIRED = 30;
    private static final String DEFAULT_MESSAGE = "你雇佣的人过多";

    private FollowerHireLimit() {
    }

    public static boolean blockIfAtLimit(RoleFollower role, ServerPlayer player) {
        if (role == null || player == null || player.server == null) return false;
        if (role.hasOwner()) return false;

        int max = ServerConfigAccess.integer(CnpcPlusConfig.MAX_HIRED_NPCS, DEFAULT_MAX_HIRED);
        if (max <= 0) return false;
        if (countOwned(player) < max) return false;

        String message = ServerConfigAccess.string(CnpcPlusConfig.HIRE_LIMIT_MESSAGE, DEFAULT_MESSAGE);
        if (message == null || message.isBlank()) message = DEFAULT_MESSAGE;
        player.displayClientMessage(Component.literal(message), false);
        return true;
    }

    private static int countOwned(ServerPlayer player) {
        int count = 0;
        String owner = player.getStringUUID();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof EntityNPCInterface npc)) continue;
                if (!(npc.role instanceof RoleFollower follower) || !follower.hasOwner()) continue;
                if (!(follower instanceof RoleFollowerOwnerAccess access)) continue;
                if (owner.equals(access.cnpcplus$getOwnerUUID())) {
                    count++;
                }
            }
        }
        return count;
    }
}
