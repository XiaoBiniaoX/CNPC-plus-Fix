package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 性能问题 4 的第二半：仇恨索敌的死逻辑（「锁到谁就跟谁一辈子」）。
 *
 * <h3>根因（javap 字节码实证）</h3>
 * {@code EntityNPCInterface.onAttack(EntityLivingBase)}：
 * <pre>
 *  0: aload_1 / ifnull 35              // 目标为 null → return
 *  4: aload_1 / aload_0 / if_acmpeq 35 // 目标是自己 → return
 *  9: aload_0
 * 10: invokevirtual isAttacking:()Z
 * 13: ifne  35                         ← ★ 已在战斗中就无条件 return，什么都不做
 * 16-24: ais.onAttack == 3 → return
 * 27-32: 目标是主人 → return
 * 35: return                           ← 死胡同
 * 36: aload_0 / aload_1
 * 38: invokespecial EntityCreature.func_70624_b   ← 且绕过自身 override
 * </pre>
 * offset 13 的 {@code ifne 35} **没有任何距离或仇恨值比较** —— 一旦 NPC 进入战斗
 * 状态，队友通过 {@code defendFaction} 发起的所有求援（{@code func_70097_a}
 * offset 486-490 的 {@code onAttack} 调用）全部被这一条吞掉。
 * 这就是哈基彬说的「原实现『最优仇恨目标』变成了『锁到谁就跟谁一辈子』」。
 *
 * <h3>对比证据：同类里的正确写法</h3>
 * 同一个类的 {@code func_70097_a} 战斗中分支（offset 287-320）**确实做了距离比较**：
 * <pre>
 * 304: func_70068_e(现有目标)   // 距离²
 * 310: func_70068_e(攻击者)     // 距离²
 * 313: dcmpl
 * 314: ifle 323                 // 现有目标更近 → 不换
 * 320: func_70624_b(攻击者)     // 攻击者更近 → 换目标
 * </pre>
 * 所以「换更近目标」的逻辑在自己被直接打到时是可达的，
 * 只有走 {@code onAttack}（队友求援）这条路径完全不可达。本修复就是把这段
 * 已被验证的比较模式补到 {@code onAttack} 上。
 *
 * <h3>另一处死逻辑（本阶段不动，仅记录）</h3>
 * {@code CombatHandler.checkTarget} 有一整套「最高仇恨值优选」（offset 76-174
 * 遍历 entrySet 比 float），但它的唯一消费者 {@code EntityAIClearTarget} 只用
 * 返回的 boolean 决定「要不要清空目标」（{@code func_75249_e} 传 null），
 * 算出的最优目标 {@code astore_1} 用完即丢。
 * 修它会显著改变现有 NPC 的战斗手感，哈基彬选的是方案 A 不是 C，故不动。
 *
 * <h3>修法</h3>
 * HEAD 注入：当「已在战斗中」且「新攻击者比现有目标更近」时，直接换目标并
 * cancel 掉原版方法（避免它走到 offset 35 的死胡同）。
 * 其余情况一律放行原版逻辑，行为完全不变。
 *
 * 用 {@code func_70624_b}（自身 override）而不是 {@code EntityCreature} 的版本 ——
 * 原版 offset 38 用 {@code invokespecial} 绕过 override 导致脚本 TargetEvent
 * 不触发，这里走正常路径把钩子还回去。
 *
 * <h3>可撤回</h3>
 * cfg 的 {@code npcRetargetCloser}，默认开。关掉即完全恢复原版行为。
 *
 * <h3>服务端安全</h3>
 * {@code onAttack} 由 {@code func_70097_a} 的服务端伤害链驱动。
 * 本混入注册 common 侧，只用 MC 实体类型，无客户端引用。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCRetarget {

    @Inject(method = "onAttack", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$retargetCloser(EntityLivingBase attacker, CallbackInfo ci) {
        if (!CnpcPlusConfig.isNpcRetargetCloserEnabled()) return;
        if (attacker == null) return;

        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (attacker == npc) return;

        // 只处理原版会吞掉的那一种情况：已在战斗中。
        // 不在战斗中时原版逻辑本来就正确，放行。
        if (!npc.isAttacking()) return;

        // 保留原版的两个否决条件（字节码 offset 16-32），不改变它们的语义。
        if (npc.ais != null && npc.ais.onAttack == 3) return;
        if (attacker == npc.getOwner()) return;

        EntityLivingBase current = npc.getAttackTarget();
        if (current == null) {
            // 战斗标志为真但没有目标 —— 直接接受新目标。
            npc.setAttackTarget(attacker);
            ci.cancel();
            return;
        }
        if (current == attacker) return;   // 已经是它，无需处理

        // 与 func_70097_a offset 299-314 同一套判据：更近才换。
        if (npc.getDistanceSq(attacker) < npc.getDistanceSq(current)) {
            npc.setAttackTarget(attacker);
            ci.cancel();
        }
    }
}
