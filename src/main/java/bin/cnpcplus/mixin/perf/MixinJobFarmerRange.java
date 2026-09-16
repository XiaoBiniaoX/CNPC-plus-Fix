package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import noppes.npcs.roles.JobFarmer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 性能问题 2：农夫职业扫描 16384 个方块 + 32768 个对象。
 *
 * <h3>根因（javap 字节码实证）</h3>
 * {@code JobFarmer} 自己不做扫描（类内 {@code func_72314_b}/{@code AxisAlignedBB}
 * 出现次数为 0），而是把范围交给 {@code MassBlockController.Update()}：
 * <pre>
 * JobFarmer.getRange():
 *   0: bipush 16
 *   2: ireturn
 *
 * MassBlockController.Update() 三层循环：
 *   68-71   x: -range..range-1   → 32 次
 *   78-81   z: -range..range-1   → 32 次
 *  120-123  y: 0..range-1        → 16 次（实际偏移 y - range/2，即 -8..+7）
 *  153: World.func_180495_p      ← 每方块 1 次 getBlockState
 *  144: new BlockData            ← 每方块 1 个对象
 *  137: BlockPos.func_177982_a   ← 每方块 1 个 BlockPos
 * </pre>
 * 32 × 32 × 16 = **16384 方块**，2 × 16384 = **32768 对象**。
 * 哈基彬给的两个数字都对，来源就是这里。
 *
 * <h3>哈基彬的「以 NPC 为中心」要求 —— 已经满足，无需改动</h3>
 * {@code Update} offset 38-47 是 {@code getNpc().func_180425_c()}，
 * 即扫描中心**本来就是 NPC 自身位置**。
 * offset 96 的 {@code bipush 64} 容易被误认成中心，但它只是
 * {@code func_175667_e}（isBlockLoaded）探测用的固定 y 坐标，
 * 与实际取样的 {@code y - range/2} 区间无关。所以这一点不需要动。
 *
 * <h3>频率修正（与哈基彬描述不符，但不影响优化方向）</h3>
 * 哈基彬说「每次扫描都能造成明显的 TPS 问题」「农夫每 tick 扫全部 trackedBlocks」。
 * 字节码显示实际频率低得多：
 * <ul>
 *   <li>全量扫描门禁 {@code aiShouldExecute} offset 123 是 {@code blockTicks > 1200}，
 *       而 {@code blockTicks} 受 vanilla {@code EntityAITasks.tickRate = 3} 限制
 *       每 3 tick 才 +1 → **每 3600 tick（3 分钟）一次**；</li>
 *   <li>{@code MassBlockController.Update} 由 {@code ServerTickHandler} 每 20 tick
 *       调用，且 offset 19 的 {@code Queue.remove()} **每次只处理一个** IMassBlock；</li>
 *   <li>{@code trackedBlocks} 遍历在 {@code aiUpdateTask}，因 {@code aiContinueExecute}
 *       恒返回 false（{@code 0: iconst_0 / 1: ireturn}），每次激活只跑一次。</li>
 * </ul>
 * 所以真实形态是「3 分钟一遇的**单帧尖峰**」而非持续负载 —— 但尖峰本身
 * （16384 次 getBlockState + 32768 次分配挤在一 tick）确实会造成可感的卡顿，
 * 值得优化。
 *
 * <h3>修法</h3>
 * 只改 {@code getRange()} 的返回值：16 → cfg 值（默认 8）。
 * 因为三层循环的上界全部来自这一个方法，改它等于同时降三个维度：
 * <pre>
 * range 16: 32 × 32 × 16 = 16384 方块 / 32768 对象
 * range  8: 16 × 16 ×  8 =  2048 方块 /  4096 对象   ← 降到 1/8
 * </pre>
 * 哈基彬说「改为原值的 1/4 也完全足够」，这里给到 1/8（半径 8 格对一片正常农田
 * 仍然绰绰有余，且可 cfg 调回）。
 *
 * 分摊由 {@link MixinMassBlockControllerSlice} 单独处理。
 *
 * <h3>服务端安全</h3>
 * {@code JobFarmer} 是服务端职业逻辑。本混入注册 common 侧，无客户端引用。
 */
@Mixin(value = JobFarmer.class, remap = false)
public class MixinJobFarmerRange {

    /**
     * {@code getRange()} 返回 int，故 handler 必须用 {@code CallbackInfoReturnable}
     * （findings 阶段 23 崩溃3 的教训）。用 {@code setReturnValue} 覆盖。
     */
    @Inject(method = "getRange", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$smallerRange(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(CnpcPlusConfig.getFarmerScanRange());
    }
}
