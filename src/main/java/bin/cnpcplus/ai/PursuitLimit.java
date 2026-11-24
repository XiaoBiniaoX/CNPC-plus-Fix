package bin.cnpcplus.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import noppes.npcs.entity.EntityNPCInterface;

public interface PursuitLimit {
    boolean cnpcplus$isPursuitLimited();
    void cnpcplus$setPursuitLimited(boolean enabled);
    float cnpcplus$getPursuitRadius();
    void cnpcplus$setPursuitRadius(float radius);

    static Vec3d home(EntityNPCInterface npc) {
        return new Vec3d(npc.getStartXPos(), npc.getStartYPos(), npc.getStartZPos());
    }

    static boolean outside(EntityNPCInterface npc, EntityLivingBase target) {
        PursuitLimit limit = (PursuitLimit) npc.advanced;
        if (!limit.cnpcplus$isPursuitLimited()) return false;
        Vec3d origin = home(npc);
        return target.getDistanceSq(origin.x, origin.y, origin.z)
                > limit.cnpcplus$getPursuitRadius() * limit.cnpcplus$getPursuitRadius();
    }
}
