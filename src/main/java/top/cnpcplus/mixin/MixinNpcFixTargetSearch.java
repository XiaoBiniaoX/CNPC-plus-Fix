package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import noppes.npcs.ai.target.NpcNearestAttackableTargetGoal;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.NpcAiOptimizerAccess;

/**
 * 241z-14: 删除索敌启动频率门，恢复 CNPC 原生随机搜索；仅跳过必定无目标的扫描。
 *
 * <p>仅当当前无目标、非 Guard/Companion、attackOtherFactions=false 且 aggroRange 内
 * 没有玩家时，直接返回 false。Guard/Companion 保留 CNPC 的特殊目标路径；
 * attackOtherFactions=true 时不做任何跳过，避免改变 NPC 对 NPC 的索敌。
 */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public abstract class MixinNpcFixTargetSearch {
    @Unique
    private EntityNPCInterface cnpcplus$testNpc() {
        if (!((Object) this instanceof NpcNearestAttackableTargetGoal<?>)) {
            return null;
        }
        if (!((Object) this instanceof TargetGoalAccessor accessor)) {
            return null;
        }
        Mob mob = accessor.customnpcsaioptimizer$getMob();
        return mob instanceof EntityNPCInterface npc ? npc : null;
    }

    @Inject(method = "m_8036_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$skipImpossibleSearch(CallbackInfoReturnable<Boolean> cir) {
        if (!NpcAiOptimizerAccess.enabled()) return;
        EntityNPCInterface npc = cnpcplus$testNpc();
        if (npc == null || npc.level() == null || npc.level().isClientSide || !npc.isAlive()) return;
        if (npc.getTarget() != null) return;
        if (!(npc.level() instanceof ServerLevel level)) return;
        if (npc.stats == null || npc.advanced == null) return;
        if (npc.advanced.attackOtherFactions) return;
        if (npc.job != null && npc.job.getType() == 3) return;
        if (npc.role != null && npc.role.getType() == 6) return;
        int aggro = npc.stats.aggroRange;
        if (aggro > 0) {
            for (ServerPlayer player : level.players()) {
                if (player.isAlive() && npc.isInRange(player, aggro)) return;
            }
        }
        cir.setReturnValue(false);
    }
}
