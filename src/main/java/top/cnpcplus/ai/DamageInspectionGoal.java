package top.cnpcplus.ai;

import java.util.EnumSet;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.LivingEntity;

/** Walk a short distance toward an unseen hit, pause briefly, then release MOVE for CNPC's return goal. */
public final class DamageInspectionGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQR = 0.5D * 0.5D;
    private static final int OBSERVE_TICKS = 20;
    private static final int MAX_TRAVEL_TICKS = 800;

    private final EntityNPCInterface npc;
    private boolean pending;
    private double targetX;
    private double targetY;
    private double targetZ;
    private int ticks;
    private int observeTicks;
    private int idleTicks;

    public DamageInspectionGoal(EntityNPCInterface npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    public void request(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.pending = true;
    }

    public void cancel() {
        this.pending = false;
        this.observeTicks = 0;
        this.ticks = 0;
        PathNavigation navigation = this.npc.getNavigation();
        if (navigation != null) navigation.stop();
    }

    public void updateTarget(double x, double y, double z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.ticks = 0;
        this.observeTicks = 0;
        this.idleTicks = 0;
        PathNavigation navigation = this.npc.getNavigation();
        if (navigation != null) navigation.moveTo(x, y, z, 1.0D);
    }

    public boolean isPending() {
        return this.pending;
    }

    @Override
    public boolean canUse() {
        return this.pending && this.npc.isAlive() && !this.npc.isKilled()
            && !this.npc.isPassenger() && !this.npc.isFollower();
    }

    @Override
    public boolean canContinueToUse() {
        return this.pending && this.npc.isAlive() && !this.npc.isKilled()
            && !this.npc.isPassenger() && !this.npc.isAttacking() && !this.npc.isInteracting()
            && (this.observeTicks > 0 || this.ticks < MAX_TRAVEL_TICKS);
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.observeTicks = 0;
        this.idleTicks = 0;
        PathNavigation navigation = this.npc.getNavigation();
        if (navigation != null) {
            navigation.moveTo(this.targetX, this.targetY, this.targetZ, 1.0D);
        }
    }

    @Override
    public void tick() {
        this.ticks++;
        LivingEntity target = this.npc.getTarget();
        if (target != null && target.isAlive() && target.distanceToSqr(this.npc) <= 64.0D) {
            this.pending = false;
            return;
        }
        double dx = this.npc.getX() - this.targetX;
        double dz = this.npc.getZ() - this.targetZ;
        if (dx * dx + dz * dz <= ARRIVAL_DISTANCE_SQR) {
            PathNavigation navigation = this.npc.getNavigation();
            if (navigation != null) navigation.stop();
            if (this.observeTicks == 0) this.observeTicks = OBSERVE_TICKS;
            if (this.observeTicks > 0) this.observeTicks--;
            else this.pending = false;
        } else if (this.ticks >= MAX_TRAVEL_TICKS) {
            this.pending = false;
        }
        if (this.npc.getNavigation() == null || this.npc.getNavigation().isDone()) {
            // CNPC/ground navigation accepts a waypoint early. Keep the final
            // gap under MoveControl so the configured inspection distance is
            // actually reached instead of stopping at the first node.
            this.npc.getMoveControl().setWantedPosition(this.targetX, this.targetY, this.targetZ, 1.0D);
            this.idleTicks++;
        } else this.idleTicks = 0;
    }

    @Override
    public void stop() {
        PathNavigation navigation = this.npc.getNavigation();
        if (navigation != null) navigation.stop();
        if (!this.npc.isAttacking() && !this.npc.isInteracting()) {
            this.npc.setCurrentAnimation(this.npc.ais.getAnimation());
            this.npc.refreshDimensions();
        }
    }
}
