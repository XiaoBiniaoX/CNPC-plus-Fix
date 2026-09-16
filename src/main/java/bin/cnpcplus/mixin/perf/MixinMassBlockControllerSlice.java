package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import noppes.npcs.ServerTickHandler;
import noppes.npcs.controllers.MassBlockController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 性能问题 2 的分摊部分（哈基彬要求「tick 要求分摊，类似 B 但不能和 B 一样」）。
 *
 * <h3>为什么不按方案 B 的「每 tick 扫固定数量方块」做</h3>
 * 哈基彬明确要求实现方式不同于 B。而字节码也支持这个判断 ——
 * 按方块数切分需要改 {@code MassBlockController.Update()} 的控制流：
 * <pre>
 * Update() 的形状：
 *   19: Queue.remove()          ← 取出一个 IMassBlock
 *   68-171: 三层循环扫完整个立方体
 *   187: IMassBlock.processed(List)  ← 一次性交付全部结果
 * </pre>
 * {@code processed} 是**一次性交付全量列表**的契约（{@code JobFarmer.processed}
 * 会据此重建整个 {@code trackedBlocks} 并去重）。如果按方块数切成多 tick，
 * 就必须自己维护「扫到哪了」的游标、跨 tick 累积 List、还要保证中途
 * NPC 移动/卸载时不交付半份数据 —— 那是重写整个方法，违反最小改动，
 * 也容易破坏 {@code trackedBlocks} 的语义。
 *
 * <h3>本方案：降低 Update 的调用频率，让多个农夫天然错开</h3>
 * {@code ServerTickHandler.onServerTick(ServerTickEvent)} offset 42 每 20 tick
 * 调一次 {@code MassBlockController.Update()}，而 {@code Update} offset 19 的
 * {@code Queue.remove()} **每次只取一个** IMassBlock。
 *
 * 所以把调用频率降到「每 {@code farmerScanSlices} 次机会才真正执行一次」后：
 * <ul>
 *   <li>单个 tick 内最多仍只有一个农夫在扫（原版就是如此）；</li>
 *   <li>但多个农夫排队时，它们被摊到更长的时间轴上，
 *       峰值密度降到 1/slices；</li>
 *   <li>{@code processed} 的一次性全量交付契约完全不变，
 *       {@code trackedBlocks} 语义零风险。</li>
 * </ul>
 * 配合 {@link MixinJobFarmerRange} 把单次规模降到 1/8，
 * 合起来单位时间的扫描成本降到约 1/32。
 *
 * <h3>为什么用 @Redirect 而不是改那个 20-tick 门禁</h3>
 * offset 31 的 {@code bipush 20} 门禁是 {@code Update} 与
 * {@code SchematicController.updateBuilding}（offset 39）**共用**的。
 * 用 {@code @ModifyConstant} 改它会连带拖慢示意图建造。
 * Redirect 单独那一次 {@code Update} 调用，影响面精确。
 *
 * <h3>队列不会堆积</h3>
 * {@code JobFarmer.aiShouldExecute} 有 {@code waitingForBlocks} 标志
 * （offset 109 `ifne 143`），排队期间不会重复入队。
 * 所以降低消费频率只会让农夫等得久一点（本来就是 3 分钟一次的低频行为），
 * 不会让队列无界增长。
 *
 * <h3>服务端安全</h3>
 * {@code ServerTickHandler} 是服务端 tick 处理器。本混入注册 common 侧，
 * 无客户端引用。
 */
@Mixin(value = ServerTickHandler.class, remap = false)
public class MixinMassBlockControllerSlice {

    /** 累计的执行机会数。每 20 tick +1（由原版门禁保证）。 */
    @Unique private int cnpcplus$slice;

    /**
     * 只在累计到 cfg 指定的份数时才真正执行扫描。
     *
     * target 指向 noppes 自有的静态方法，无 SRG 映射，写原名。
     */
    @Redirect(method = "onServerTick(Lnet/minecraftforge/fml/common/gameevent/TickEvent$ServerTickEvent;)V",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/controllers/MassBlockController;Update()V"),
            remap = false, require = 1)
    private void cnpcplus$sliceMassScan() {
        int slices = CnpcPlusConfig.getFarmerScanSlices();
        if (slices <= 1) {
            MassBlockController.Update();
            return;
        }
        if (++this.cnpcplus$slice < slices) {
            return;
        }
        this.cnpcplus$slice = 0;
        MassBlockController.Update();
    }
}
