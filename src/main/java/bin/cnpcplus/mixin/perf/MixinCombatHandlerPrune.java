package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.ai.CombatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.Map;

/**
 * 性能问题 14：{@code CombatHandler.aggressors} 无上限、强引用实体，长期战斗后内存不断增长。
 *
 * <h3>根因（javap 字节码实证）</h3>
 * 字段声明是 {@code Map<EntityLivingBase, Float>} —— **key 是实体的裸强引用**，
 * 不是 UUID、不是 entityId、不是 WeakReference。
 *
 * 写入只有一处：{@code damage(DamageSource, float)} offset 66 的 {@code Map.put}，
 * 由 {@code EntityNPCInterface.func_70665_d}（每次实际掉血）驱动，
 * 累加值 {@code fadd} 无上限、无衰减、无时间加权。
 *
 * 清理只有一处：{@code reset()} offset 9 的 {@code Map.clear}，但它需要
 * {@code shouldCombatContinue()} **连续 41 tick 为 false**（{@code update()}
 * offset 67 `bipush 40` + offset 77-79 一旦战斗继续就把 {@code combatResetTimer}
 * 清零）。所以**持续处于战斗中的 NPC（守卫、boss、被玩家轮流拉的怪）从头到尾
 * 一次都不会 clear**。
 *
 * 后果是双重的：
 * <ol>
 *   <li>Map 无界增长；</li>
 *   <li>死亡 / 登出 / 区块卸载的 {@code EntityLivingBase} 被钉在堆上无法 GC，
 *       而它们通过 {@code Entity.field_70170_p} 又强引用整个 {@code World}。</li>
 * </ol>
 * 附带：{@code checkTarget} 每 10 tick 要全量遍历这个只增不减的 map。
 *
 * <h3>为什么不在 checkTarget 的遍历里顺手 remove</h3>
 * 我最初的方案是「offset 143 已经调了 {@code isValidTarget}，offset 146 的
 * {@code ifeq 171} 白白跳过，顺手 remove 即可」。但重读字节码发现 **offset 129
 * 有个前置的 {@code ifle 171}**：
 * <pre>
 * 128: fcmpl                    // entry.value 与当前最高仇恨比较
 * 129: ifle  171                // ← 仇恨值不更高就直接跳过，根本走不到 isValidTarget
 * 143: invokevirtual isValidTarget
 * 146: ifeq  171
 * </pre>
 * 也就是说仇恨值较低的条目**永远不会被检查有效性**，在那里清理会漏掉它们 ——
 * 而恰恰是这些低仇恨的陈旧条目最容易堆积。
 *
 * 所以改为在 {@code checkTarget} 的 RETURN 独立扫一遍全表清理。
 * 频率与原版遍历同频（每 10 tick，由 offset 19-22 的 {@code irem} 门禁保证），
 * 不额外增加遍历次数。
 *
 * <h3>清理判据</h3>
 * 不复用 {@code isValidTarget}（它还含 {@code isInRange} 距离判断，
 * 目标暂时走远不代表仇恨该忘掉）。只清「实体已死 / 已被移除」这类**不可能再回来**
 * 的条目 —— 这既解决内存泄漏，又不改变战斗手感。
 *
 * <h3>服务端安全</h3>
 * {@code CombatHandler} 是服务端 AI 组件。本混入注册在 common 侧，
 * 只用 {@code java.util} 与 MC 实体类型，无客户端引用。
 */
@Mixin(value = CombatHandler.class, remap = false)
public class MixinCombatHandlerPrune {

    /** CombatHandler 自有的 private 字段，@Shadow 可正常解析。 */
    @Shadow private Map<EntityLivingBase, Float> aggressors;

    /**
     * 在原版每 10 tick 的仇恨遍历之后，顺带清掉不可能再回来的条目。
     *
     * 用 RETURN 而不是 HEAD：HEAD 时机上早于原版的最优目标计算，
     * 若此刻移除了当前目标对应的条目会影响它的判断。RETURN 时原版已算完。
     *
     * {@code checkTarget} 返回 boolean，故 handler 必须用
     * {@code CallbackInfoReturnable}（findings 阶段 23 崩溃3 / 阶段 25 的教训）。
     */
    @Inject(method = "checkTarget", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$pruneDeadAggressors(CallbackInfoReturnable<Boolean> cir) {
        if (!CnpcPlusConfig.isPruneCombatAggressors()) return;
        if (this.aggressors == null || this.aggressors.isEmpty()) return;

        Iterator<Map.Entry<EntityLivingBase, Float>> it = this.aggressors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<EntityLivingBase, Float> entry = it.next();
            EntityLivingBase attacker = entry.getKey();
            // 只清「不可能再回来」的：null / 已死 / 已从世界移除。
            // 刻意不看距离 —— 走远的敌人回来时仇恨应当还在，那是玩法而非泄漏。
            if (attacker == null || attacker.isDead || !attacker.isEntityAlive()) {
                it.remove();
            }
        }
    }
}
