package top.cnpcplus.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import noppes.npcs.ai.target.NpcNearestAttackableTargetGoal;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.common.util.LogWriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
 *
 * <p>241f 诊断日志：仅在 scan 通过门时记录 findTarget 前后状态（每 40t 限流）。
 */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public abstract class MixinNpcFixTargetSearch {
    @Unique
    private long cnpcplus$lastDiagLog = Long.MIN_VALUE;
    @Unique
    private long cnpcplus$lastEntryLog = Long.MIN_VALUE;

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

    /** 241h 无条件入口日志：确认 m_8036_ 是否被调用（限流 40t，不受 scan 门限制）。 */
    @Inject(method = "m_8036_", at = @At("HEAD"), remap = false)
    private void cnpcplus$diagEntry(CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface npc = cnpcplus$testNpc();
        if (npc == null || npc.level() == null) return;
        long now = npc.level().getGameTime();
        if (now - cnpcplus$lastEntryLog < 40L) return;
        cnpcplus$lastEntryLog = now;
        LogWriter.warn("[tgt] ENTRY id=" + npc.getId() + " t=" + now + " curTgt=" + npc.getTarget()
                + " enabled=" + NpcAiOptimizerAccess.enabled() + " interval=" + NpcAiOptimizerAccess.targetInterval());
    }

    @Redirect(method = "m_8036_", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;m_188503_(I)I"), remap = false)
    private int cnpcplus$forceTargetScan(RandomSource random, int bound) {
        if (NpcAiOptimizerAccess.enabled() && NpcAiOptimizerAccess.targetInterval() > 4
                && (Object) this instanceof NpcNearestAttackableTargetGoal<?>) {
            return 0;
        }
        return random.nextInt(bound);
    }

    /** 241f 诊断：scan 真正通过门进入 findTarget 时记一条（限流 40t）。 */
    @Inject(method = "m_8036_", at = @At("HEAD"), remap = false)
    private void cnpcplus$diagScanEnter(CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface npc = cnpcplus$testNpc();
        if (npc == null || npc.level() == null) return;
        // 仅在真正会走到 findTarget 的 scan tick 记（非节流阻塞时）
        int interval = NpcAiOptimizerAccess.targetInterval();
        long tick = npc.level().getGameTime() + (NpcAiOptimizerAccess.staggerTargets() ? npc.getId() : 0);
        if (interval > 4 && Math.floorMod(tick, interval) != 0L) return;
        long now = npc.level().getGameTime();
        if (now - cnpcplus$lastDiagLog < 40L) return;
        cnpcplus$lastDiagLog = now;
        double follow = npc.getAttributeValue(Attributes.FOLLOW_RANGE);
        LogWriter.warn("[tgt] scanEnter id=" + npc.getId() + " t=" + now + " curTgt=" + npc.getTarget()
                + " follow=" + follow + " aggro=" + (npc.stats == null ? -1 : npc.stats.aggroRange)
                + " los=" + (npc.ais != null ? npc.ais.directLOS : "?")
                + " invisible=" + (npc.ais != null ? npc.ais.attackInvisible : "?"));
    }

    /** 241f 诊断：findTarget 后结果（RETURN，限流 40t）。 */
    @Inject(method = "m_8036_", at = @At("RETURN"), remap = false)
    private void cnpcplus$diagScanResult(CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface npc = cnpcplus$testNpc();
        if (npc == null || npc.level() == null) return;
        int interval = NpcAiOptimizerAccess.targetInterval();
        long tick = npc.level().getGameTime() + (NpcAiOptimizerAccess.staggerTargets() ? npc.getId() : 0);
        if (interval > 4 && Math.floorMod(tick, interval) != 0L) return;
        long now = npc.level().getGameTime();
        if (now - cnpcplus$lastDiagLog < 39L) return; // 与 enter 配对，紧邻
        LogWriter.warn("[tgt] scanResult id=" + npc.getId() + " t=" + now + " ret=" + cir.getReturnValueZ() + " newTgt=" + npc.getTarget());
    }

    /** 241f 诊断：start（真正拿到目标）。 */
    @Inject(method = "m_8056_", at = @At("RETURN"), remap = false)
    private void cnpcplus$diagStart(CallbackInfo ci) {
        EntityNPCInterface npc = cnpcplus$testNpc();
        if (npc == null || npc.level() == null) return;
        LogWriter.warn("[tgt] start id=" + npc.getId() + " t=" + npc.level().getGameTime() + " tgt=" + npc.getTarget());
    }
}
