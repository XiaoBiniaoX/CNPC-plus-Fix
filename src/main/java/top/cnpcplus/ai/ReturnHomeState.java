package top.cnpcplus.ai;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Return goal owns this destination from navigation start until stop. */
public final class ReturnHomeState {

    private static final Map<EntityNPCInterface, Vec3> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ReturnHomeState() {
    }

    public static void start(EntityNPCInterface npc, Vec3 target) {
        ACTIVE.put(npc, target);
    }

    public static Vec3 end(EntityNPCInterface npc) {
        return ACTIVE.remove(npc);
    }

    public static Vec3 target(EntityNPCInterface npc) {
        return ACTIVE.get(npc);
    }

    public static boolean arrived(EntityNPCInterface npc, Vec3 target) {
        return Math.abs(target.x - npc.getX()) <= 0.02 && Math.abs(target.z - npc.getZ()) <= 0.02
                && Math.abs(target.y - npc.getY()) <= 1.0;
    }

    public static Vec3 finalApproach(EntityNPCInterface npc) {
        Vec3 target = target(npc);
        if (target == null || !(npc.getNavigation() instanceof GroundPathNavigation navigation)
                || !navigation.isDone() || !npc.onGround() || npc.isInWaterOrBubble() || npc.onClimbable()
                || Math.abs(target.y - npc.getY()) > npc.getStepHeight()) return null;
        double dx = target.x - npc.getX(), dz = target.z - npc.getZ();
        return dx * dx + dz * dz <= 1.5 * 1.5 ? target : null;
    }
}
