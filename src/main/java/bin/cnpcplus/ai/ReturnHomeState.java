package bin.cnpcplus.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.Vec3d;
import noppes.npcs.entity.EntityNPCInterface;

/** Owned by EntityAIReturn from navigate until resetTask; server thread only. */
public final class ReturnHomeState {
    private static final Map<EntityNPCInterface, Vec3d> ACTIVE = new WeakHashMap<>();

    private ReturnHomeState() {}

    public static void start(EntityNPCInterface npc, Vec3d target) { ACTIVE.put(npc, target); }
    public static Vec3d target(EntityNPCInterface npc) { return ACTIVE.get(npc); }
    public static Vec3d end(EntityNPCInterface npc) { return ACTIVE.remove(npc); }

    public static boolean arrived(EntityNPCInterface npc, Vec3d target) {
        return Math.abs(target.x - npc.posX) <= 0.02D && Math.abs(target.z - npc.posZ) <= 0.02D
                && Math.abs(target.y - npc.posY) <= Math.max(1.0D, npc.stepHeight);
    }

    public static Vec3d finalApproach(EntityNPCInterface npc) {
        Vec3d target = target(npc);
        if (target == null || !(npc.getNavigator() instanceof PathNavigateGround)
                || !npc.getNavigator().noPath() || !npc.onGround || npc.isInWater()
                || npc.isInLava() || npc.isOnLadder()
                || Math.abs(target.y - npc.posY) > Math.max(1.0D, npc.stepHeight)) return null;
        double dx = target.x - npc.posX, dz = target.z - npc.posZ;
        return dx * dx + dz * dz <= 2.25D ? target : null;
    }
}
