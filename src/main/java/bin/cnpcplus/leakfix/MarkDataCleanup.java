package bin.cnpcplus.leakfix;

import bin.cnpcplus.mixin.leakfix.MarkDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.Map;

/**
 * 实体离开世界时清理 MarkData.dataMap，防止实体 ID 复用串数据和内存泄露。
 *
 * <p>MarkData.dataMap 以实体 ID 为键，实体移除后条目残留。
 * 实体 ID 被复用时新实体命中旧条目，拿到上一个实体的标记数据。
 */
public final class MarkDataCleanup {

    private MarkDataCleanup() {
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        Map<Integer, noppes.npcs.controllers.data.MarkData> dataMap = MarkDataAccessor.cnpcplus$dataMap();
        dataMap.remove(event.getEntity().getId());
    }
}
