package bin.cnpcplus.ai;

import bin.cnpcplus.common.IDamageInspectionAccess;
import net.minecraft.entity.ai.EntityAIBase;
import noppes.npcs.constants.AiMutex;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * 238-13：受到看不见的远程伤害后，走向弹道来源方向观察一小段。
 * 到达后停留 20 tick，再交还移动权给 CNPC 的返回/闲逛 AI。
 */
public final class EntityAIInspectDamage extends EntityAIBase {

    private static final double ARRIVAL_DISTANCE_SQR = 0.25D;
    private static final int OBSERVE_TICKS = 20;
    private static final int MAX_TRAVEL_TICKS = 800;

    private final EntityNPCInterface npc;
    private boolean pending;
    private boolean running;
    private double targetX;
    private double targetY;
    private double targetZ;
    private int ticks;
    private int observeTicks;

    public EntityAIInspectDamage(EntityNPCInterface npc) {
        this.npc = npc;
        this.setMutexBits(AiMutex.PASSIVE | AiMutex.PATHING);
    }

    public void updateTarget(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.ticks = 0;
        this.observeTicks = 0;
        this.pending = true;
        if (this.running) this.npc.getNavigator().tryMoveToXYZ(x, y, z, 1.0D);
    }

    public void cancelInspection() {
        this.pending = false;
        this.ticks = 0;
        this.observeTicks = 0;
        if (this.running && this.npc.getNavigator() != null) {
            this.npc.getNavigator().clearPath();
        }
    }

    public boolean isPending() {
        return this.pending;
    }

    @Override
    public boolean shouldExecute() {
        return this.pending && this.npc.isEntityAlive() && !this.npc.isKilled()
                && !this.npc.isRiding() && !this.npc.isBeingRidden() && !this.npc.isFollower()
                && !this.npc.isAttacking() && !this.npc.isInteracting()
                && ((IDamageInspectionAccess) this.npc.advanced).cnpcplus$isDamageInspectionEnabled();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.shouldExecute() && this.ticks < MAX_TRAVEL_TICKS;
    }

    @Override
    public void startExecuting() {
        this.running = true;
        this.ticks = 0;
        this.observeTicks = 0;
        this.npc.setCurrentAnimation(0);
        this.npc.updateHitbox();
        if (this.npc.getNavigator() != null) {
            this.npc.getNavigator().tryMoveToXYZ(this.targetX, this.targetY, this.targetZ, 1.0D);
        }
    }

    @Override
    public void updateTask() {
        this.ticks++;
        if (this.npc.getAttackTarget() != null && this.npc.getAttackTarget().isEntityAlive()
                && this.npc.getDistanceSq(this.npc.getAttackTarget()) <= 64.0D) {
            this.pending = false;
            return;
        }

        double dx = this.npc.posX - this.targetX;
        double dz = this.npc.posZ - this.targetZ;
        if (dx * dx + dz * dz <= ARRIVAL_DISTANCE_SQR) {
            if (this.npc.getNavigator() != null) {
                this.npc.getNavigator().clearPath();
            }
            if (++this.observeTicks >= OBSERVE_TICKS) {
                this.pending = false;
            }
            return;
        } else if (this.ticks >= MAX_TRAVEL_TICKS) {
            this.pending = false;
            return;
        }

        if (this.ticks % 20 == 0) {
            this.npc.getNavigator().tryMoveToXYZ(this.targetX, this.targetY, this.targetZ, 1.0D);
        }
        if (this.npc.getNavigator() == null || this.npc.getNavigator().noPath()) {
            if (this.npc.getMoveHelper() != null) {
                this.npc.getMoveHelper().setMoveTo(this.targetX, this.targetY, this.targetZ, 1.0D);
            }
        }
    }

    @Override
    public void resetTask() {
        this.cancelInspection();
        this.running = false;
        if (!this.npc.isAttacking() && !this.npc.isInteracting()
                && (!this.npc.ais.shouldReturnHome() || this.npc.isVeryNearAssignedPlace())) {
            this.npc.setCurrentAnimation(this.npc.ais.getAnimation());
            this.npc.updateHitbox();
        }
    }
}
