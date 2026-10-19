package top.cnpcplus.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAdvanced;
import top.cnpcplus.mixin.MobGoalSelectorAccess;

public final class DamageInspectionController {
    private static final double ATTACKER_RANGE = 8.0D;
    private static final Map<EntityNPCInterface, DamageInspectionGoal> GOALS = new WeakHashMap<>();

    private DamageInspectionController() {}

    public static void install(EntityNPCInterface npc) {
        if (npc == null || npc.level().isClientSide) return;
        DamageInspectionGoal goal;
        synchronized (GOALS) {
            goal = GOALS.computeIfAbsent(npc, DamageInspectionGoal::new);
        }
        MobGoalSelectorAccess access = (MobGoalSelectorAccess) npc;
        boolean installed = access.cnpcplus$getGoalSelector().getAvailableGoals().stream()
            .anyMatch(wrapped -> wrapped.getGoal() == goal);
        if (!installed) access.cnpcplus$getGoalSelector().addGoal(0, goal);
    }

    public static void remove(EntityNPCInterface npc) {
        if (npc == null) return;
        DamageInspectionGoal goal;
        synchronized (GOALS) { goal = GOALS.remove(npc); }
        if (goal == null) return;
        goal.cancel();
        MobGoalSelectorAccess access = (MobGoalSelectorAccess) npc;
        for (WrappedGoal wrapped : access.cnpcplus$getGoalSelector().getAvailableGoals()) {
            if (wrapped.getGoal() == goal) access.cnpcplus$getGoalSelector().removeGoal(goal);
        }
    }

    public static void reset(EntityNPCInterface npc) {
        synchronized (GOALS) {
            DamageInspectionGoal goal = GOALS.get(npc);
            if (goal != null) goal.cancel();
        }
    }

    public static void onHurt(EntityNPCInterface npc, DamageSource source, float amount) {
        if (npc == null || npc.level().isClientSide || amount <= 0 || npc.isKilled()
                || !((DamageInspectionAccess) npc.advanced).cnpcplus$isDamageInspectionEnabled()) return;

        DamageInspectionGoal existing;
        synchronized (GOALS) { existing = GOALS.get(npc); }
        if (existing != null && existing.isPending()) existing.cancel();

        Entity sourceEntity = NoppesUtilServer.GetDamageSourcee(source);
        if (sourceEntity instanceof LivingEntity attacker && attacker.isAlive()
                && attacker.distanceToSqr(npc) <= ATTACKER_RANGE * ATTACKER_RANGE) return;

        Vec3 direction = null;
        if (sourceEntity != null) {
            direction = new Vec3(sourceEntity.getX() - npc.getX(), 0, sourceEntity.getZ() - npc.getZ());
        }
        Vec3 sourcePosition = source.getSourcePosition();
        if ((direction == null || direction.horizontalDistanceSqr() < 1.0E-6D)
                && sourcePosition != null) {
            Vec3 origin = sourcePosition;
            direction = new Vec3(origin.x - npc.getX(), 0, origin.z - npc.getZ());
        }
        if (direction == null || direction.horizontalDistanceSqr() < 1.0E-6D) {
            Vec3 motion = npc.getDeltaMovement();
            direction = new Vec3(motion.x, 0, motion.z);
        }
        if (direction.horizontalDistanceSqr() < 1.0E-6D) return;

        direction = direction.normalize();
        double inspectDistance = ((DamageInspectionAccess) npc.advanced).cnpcplus$getInspectionDistance();
        double x = npc.getX() + direction.x * inspectDistance;
        double z = npc.getZ() + direction.z * inspectDistance;
        double y = npc.getY();
        DamageInspectionGoal goal;
        synchronized (GOALS) {
            goal = GOALS.computeIfAbsent(npc, DamageInspectionGoal::new);
        }
        goal.updateTarget(x, y, z);
        // A distant projectile source is not visible; inspection replaces that stale aggro response.
        if (npc.getTarget() != null && npc.getTarget().distanceToSqr(npc) > ATTACKER_RANGE * ATTACKER_RANGE) {
            npc.setTarget(null);
        }
    }
}
