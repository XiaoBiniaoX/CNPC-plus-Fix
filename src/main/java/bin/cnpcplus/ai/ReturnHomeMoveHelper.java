package bin.cnpcplus.ai;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.util.math.Vec3d;
import noppes.npcs.entity.EntityNPCInterface;

/** Only owns the final ground segment. Other movement uses the native helper. */
public final class ReturnHomeMoveHelper extends EntityMoveHelper {
    private final EntityNPCInterface npc;

    public ReturnHomeMoveHelper(EntityNPCInterface npc) {
        super(npc);
        this.npc = npc;
    }

    @Override
    public void onUpdateMoveHelper() {
        Vec3d target = ReturnHomeState.finalApproach(npc);
        if (target == null) {
            super.onUpdateMoveHelper();
            return;
        }
        action = Action.WAIT;
        npc.setAIMoveSpeed(0);
        npc.setMoveForward(0);
        npc.setMoveStrafing(0);
        npc.setJumping(false);
        double dx = target.x - npc.posX, dz = target.z - npc.posZ;
        double distance = Math.hypot(dx, dz);
        if (ReturnHomeState.arrived(npc, target)) {
            npc.motionX = npc.motionZ = 0;
            return;
        }
        // Replace residual motion too: high speed cannot carry the NPC past home.
        double step = Math.min(distance * 0.5D,
                npc.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue());
        npc.motionX = dx / distance * step;
        npc.motionZ = dz / distance * step;
        npc.rotationYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        npc.renderYawOffset = npc.rotationYawHead = npc.rotationYaw;
    }
}
