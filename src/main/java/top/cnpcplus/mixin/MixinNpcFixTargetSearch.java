package top.cnpcplus.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import noppes.npcs.ai.target.NpcNearestAttackableTargetGoal;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.NpcAiOptimizerAccess;

/**
 * 敌对 NPC 索敌节流（npcfix target_search_interval_ticks）+ 双重节流修复（240 #4）。
 *
 * <p>根因（用户实测「敌对 NPC 站面前也不主动追，打一下才追」）：原版
 * {@code NearestAttackableTargetGoal.m_8036_}（canUse）内自带
 * {@code random.nextInt(reducedTickDelay(10)) != 0 → false}（约 1/5 通过），
 * 与本 mixin 的 1/interval 门相乘 ≈ 1/100 tick（interval=20 时约 5s）才真正
 * findTarget 一次。修复：节流生效且通过 1/interval 门后，@Redirect 强制该
 * nextInt 返回 0（javap 实证 m_8036_ 内仅此一处 RandomSource.m_188503_ 调用），
 * 节流周期由本门独占 = 1/interval（20t 即 1s）扫描一次。
 *
 * <p>非 NPC 目标（原版僵尸等）、npcfix 关闭、interval&lt;=4（门停用）一律透传
 * {@code random.nextInt(bound)}，原版行为不变；HEAD 门已保证走到 nextInt 的必是
 * 合法存活 NPC（aggro&gt;0），故 redirect 无需重复那些校验。
 */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public abstract class MixinNpcFixTargetSearch {
    @Inject(method = "m_8036_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$throttle(CallbackInfoReturnable<Boolean> cir) {
        if (!NpcAiOptimizerAccess.enabled() || !((Object) this instanceof NpcNearestAttackableTargetGoal<?>)) return;
        if (!((Object) this instanceof TargetGoalAccessor accessor)) return;
        Mob mob = accessor.customnpcsaioptimizer$getMob();
        if (!(mob instanceof EntityNPCInterface npc) || !npc.isAlive() || npc.stats == null || npc.stats.aggroRange <= 0) {
            cir.setReturnValue(false);
            return;
        }
        int interval = NpcAiOptimizerAccess.targetInterval();
        long tick = npc.level().getGameTime() + (NpcAiOptimizerAccess.staggerTargets() ? npc.getId() : 0);
        if (interval > 4 && Math.floorMod(tick, interval) != 0L) cir.setReturnValue(false);
    }

    @Redirect(method = "m_8036_", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;m_188503_(I)I"), remap = false)
    private int cnpcplus$forceTargetScan(RandomSource random, int bound) {
        if (NpcAiOptimizerAccess.enabled() && NpcAiOptimizerAccess.targetInterval() > 4
                && (Object) this instanceof NpcNearestAttackableTargetGoal<?>) {
            return 0;
        }
        return random.nextInt(bound);
    }
}
