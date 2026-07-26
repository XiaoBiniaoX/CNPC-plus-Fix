package bin.cnpcplus.util;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import bin.cnpcplus.mixin.ChunkMapMixin;
import bin.cnpcplus.mixin.ServerChunkCacheMixin;
import bin.cnpcplus.mixin.TrackedEntityMixin;

public final class ServerEntityHelper {
    private ServerEntityHelper() {}

    public static ServerEntity getServerEntity(Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            ChunkMapMixin chunkMap = (ChunkMapMixin) ((ServerChunkCacheMixin) serverLevel.getChunkSource()).chunkMap();
            Object tracked = chunkMap.entityMap().get(entity.getId());
            if (tracked != null) {
                return ((TrackedEntityMixin) tracked).serverEntity();
            }
        }
        return null;
    }
}
