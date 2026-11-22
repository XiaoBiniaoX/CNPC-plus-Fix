package top.cnpcplus.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import noppes.npcs.entity.EntityNPCInterface;

public interface PursuitLimit {
    boolean cnpcplus$isPursuitLimited();
    void cnpcplus$setPursuitLimited(boolean enabled);
    float cnpcplus$getPursuitRadius();
    void cnpcplus$setPursuitRadius(float radius);

    static Vec3 home(EntityNPCInterface npc) {
        return new Vec3(npc.getStartXPos(), npc.getStartYPos(), npc.getStartZPos());
    }

    static boolean outside(EntityNPCInterface npc, LivingEntity target) {
        var limit = (PursuitLimit)npc.advanced;
        float radius = limit.cnpcplus$getPursuitRadius();
        return limit.cnpcplus$isPursuitLimited() && target.distanceToSqr(home(npc)) > radius * radius;
    }

    static void filterTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide && event.getNewTarget() != null) {
            var state = (NpcControlAccess)npc;
            if (state.cnpcplus$isFrozen() || state.cnpcplus$isDisengaging() || outside(npc, event.getNewTarget())) event.setCanceled(true);
        }
    }
}
