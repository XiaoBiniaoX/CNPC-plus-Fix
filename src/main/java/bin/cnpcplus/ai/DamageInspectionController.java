package bin.cnpcplus.ai;

import bin.cnpcplus.common.IDamageInspectionAccess;
import bin.cnpcplus.common.INpcTasksAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.util.DamageSource;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;

/** 238-13：把「受伤后走向弹道来源视察」接到 CNPC 的任务列表。 */
public final class DamageInspectionController {

    private static final double ATTACKER_RANGE = 8.0D;

    private DamageInspectionController() {
    }

    /** 只在 EntityNPCInterface.updateTasks 重建任务表后调用，避免重复添加。 */
    public static void install(EntityNPCInterface npc) {
        if (npc == null || npc.world == null || npc.world.isRemote) return;
        if (find(npc) == null) {
            ((INpcTasksAccess) npc).cnpcplus$getTasks().addTask(0, new EntityAIInspectDamage(npc));
        }
    }

    private static EntityAIInspectDamage find(EntityNPCInterface npc) {
        if (npc == null || !(npc instanceof INpcTasksAccess)) return null;
        for (EntityAITasks.EntityAITaskEntry entry : ((INpcTasksAccess) npc).cnpcplus$getTasks().taskEntries) {
            if (entry.action instanceof EntityAIInspectDamage) {
                return (EntityAIInspectDamage) entry.action;
            }
        }
        return null;
    }

    public static void reset(EntityNPCInterface npc) {
        EntityAIInspectDamage goal = find(npc);
        if (goal != null) goal.cancelInspection();
    }

    public static void onHurt(EntityNPCInterface npc, DamageSource source, float amount) {
        if (npc == null || source == null || npc.world == null || npc.world.isRemote || amount <= 0.0F) return;
        if (npc.isKilled() || npc.advanced == null
                || !((IDamageInspectionAccess) npc.advanced).cnpcplus$isDamageInspectionEnabled()) {
            return;
        }

        reset(npc);
        Entity sourceEntity = NoppesUtilServer.GetDamageSourcee(source);
        if (sourceEntity instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) sourceEntity;
            if (attacker.isEntityAlive() && attacker.getDistanceSq(npc) <= ATTACKER_RANGE * ATTACKER_RANGE) {
                return;
            }
        }

        double dx = 0.0D;
        double dz = 0.0D;
        if (sourceEntity != null) {
            dx = sourceEntity.posX - npc.posX;
            dz = sourceEntity.posZ - npc.posZ;
        }
        if (dx * dx + dz * dz < 1.0E-6D) {
            dx = npc.motionX;
            dz = npc.motionZ;
        }
        if (dx * dx + dz * dz < 1.0E-6D) return;

        double length = Math.sqrt(dx * dx + dz * dz);
        double inspectDistance = ((IDamageInspectionAccess) npc.advanced).cnpcplus$getInspectionDistance();
        double x = npc.posX + dx / length * inspectDistance;
        double z = npc.posZ + dz / length * inspectDistance;

        install(npc);
        EntityAIInspectDamage goal = find(npc);
        if (goal == null) return;
        goal.updateTarget(x, npc.posY, z);

        EntityLivingBase current = npc.getAttackTarget();
        if (current != null && current.getDistanceSq(npc) > ATTACKER_RANGE * ATTACKER_RANGE) {
            npc.setAttackTarget(null);
        }
    }
}
