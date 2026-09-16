package bin.cnpcplus.mixin.perf;

import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.ai.EntityAIFollow;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 性能问题 9：Follow 寻路失败后每 10 tick 无限重试传送，没有任何退避。
 *
 * <h3>根因（javap 字节码实证）</h3>
 * {@code EntityAIFollow.func_75246_d}（updateTask）：
 * <pre>
 *  2-7 : updateTick++
 * 11-16: updateTick < 10 → return           ← 门禁恒为 10 tick
 * 21-22: updateTick = 0                     ← ★ 无条件重置，不看 tpTo 结果
 * 109  : PathNavigate.func_75497_a(...)     ← tryMoveToEntityLiving
 * 112  : ifne 132                            → 寻路成功则 return
 * 126  : isInRange(owner, 16.0)
 * 129  : ifeq 133                             → 失败但在 16 格内则 return
 * 141  : EntityNPCInterface.tpTo(owner)      ← 失败也没有返回值可感知
 * 144  : return
 * </pre>
 * 类只有三个字段（{@code npc}、{@code owner}、{@code updateTick}），
 * **没有失败计数器**。offset 21-22 是无条件 {@code iconst_0 / putfield}，
 * 所以不论传送成功、失败还是压根没调用，间隔恒为 10 tick。
 * 全 mod 的 {@code retries} 只存在于 {@code EntityAIMovingPath}，
 * {@code courseChangeCooldown} 只存在于 {@code FlyingMoveHelper}，
 * 这条路径上都没有。
 *
 * 而 {@code tpTo} 单次约 28 次 {@code getBlockState}（3×3 嵌套 × 每次最多 2 次
 * + {@code calculateTopPos} 内的调用）。寻路持续失败时就是稳态的
 * 每 10 tick 一轮 A* + 28 次方块查询，与问题 8 叠加。
 *
 * <h3>修法：指数退避</h3>
 * 拦下 offset 21-22 那次「无条件重置」—— 不是真的拦字段写入（那样要 FIELD 注入
 * 且拿不到上下文），而是拦 offset 141 的 {@code tpTo} 调用：
 * <ul>
 *   <li>传送后把 {@code updateTick} 倒扣一个惩罚值，使下次要等更久；</li>
 *   <li>惩罚按 10 → 20 → 40 → 80 递增，上限 200 tick（10 秒）；</li>
 *   <li>寻路成功（走 offset 112 的 {@code ifne 132} 分支，不会调到 tpTo）
 *       时自然不累加，且下一次进入本 handler 前会被重置。</li>
 * </ul>
 * 这样「偶尔失败」几乎无感（第一次仍是 10 tick），
 * 「持续失败」则从 2 次/秒降到 0.1 次/秒，降幅 20 倍。
 *
 * <h3>为什么不改 tpTo 让它返回 boolean</h3>
 * 那要改方法签名，牵连 {@code EntityAIFollow} 之外的调用点
 * （{@code ItemNpcWand}、{@code ItemSoulstoneFilled} 等也用 tpTo 语义相近的传送）。
 * 而退避本身不需要知道成功与否 —— 「调用了 tpTo」本身就意味着
 * 「寻路失败且距离超过 16 格」，这已经是足够的失败信号。
 *
 * <h3>成功归零</h3>
 * {@code func_75249_e}（startExecuting）offset 1-3 会把 {@code updateTick}
 * 设成 10（首次激活立即满足门禁）。重新开始跟随时惩罚自然清零，
 * 所以不需要额外的归零逻辑。
 *
 * <h3>服务端安全</h3>
 * AI 只在服务端 tick。本混入注册 common 侧，无客户端引用。
 */
@Mixin(value = EntityAIFollow.class, remap = false)
public class MixinEntityAIFollowBackoff {

    /** 当前惩罚 tick 数。0 = 无惩罚（首次失败仍按原版 10 tick 重试）。 */
    @Unique private int cnpcplus$penalty;

    /** 惩罚上限，10 秒。再长会让跟随体验明显变差。 */
    private static final int MAX_PENALTY = 200;

    /**
     * 拦 {@code tpTo} 调用：照常传送，但顺带累加退避惩罚。
     *
     * {@code tpTo} 是 noppes 自有方法，无 SRG 映射，target 写原名。
     */
    @Redirect(method = "func_75246_d",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;tpTo(Lnet/minecraft/entity/EntityLivingBase;)V"),
            remap = false, require = 1)
    private void cnpcplus$backoffAfterTeleport(EntityNPCInterface npc, EntityLivingBase owner) {
        // 惩罚未走完：跳过这次传送，把剩余惩罚记回 updateTick。
        // 原版已在 offset 21-22 把 updateTick 清零，这里倒扣即可延后下次触发。
        if (this.cnpcplus$penalty > 0) {
            EntityAIFollow self = (EntityAIFollow) (Object) this;
            self.updateTick = -this.cnpcplus$penalty;
            this.cnpcplus$penalty = Math.min(MAX_PENALTY, this.cnpcplus$penalty * 2);
            return;
        }
        // 首次失败：照原样传送，并开启退避（下次要多等 10 tick）。
        npc.tpTo(owner);
        this.cnpcplus$penalty = 10;
    }
}
