package top.cnpcplus.mixin;

import top.cnpcplus.perf.MassBlockScanState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.StemGrownBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.controllers.MassBlockController;
import noppes.npcs.controllers.data.BlockData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobFarmer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * 优化 #2：{@code MassBlockController.Update()} 的单 tick 全量扫描。
 *
 * <h2>原版做了什么</h2>
 * {@code MassBlockController.Update()}（反编译源码 29-53 行）由
 * {@code ServerTickHandler.onServerTick} 第 107 行每 21 tick 调用一次，
 * 每次从队列取<b>一个</b>请求者，然后在<b>同一个 tick 内</b>：
 * <pre>
 * int range = imb.getRange();                              // JobFarmer.getRange() 返回 16
 * ArrayList&lt;BlockData&gt; list = new ArrayList&lt;&gt;();
 * for (int x = -range; x &lt; range; ++x) {                   // 32 次
 *     for (int z = -range; z &lt; range; ++z) {               // 32 次
 *         if (!level.hasChunkAt(new BlockPos(x + pos.getX(), 64, z + pos.getZ()))) continue;
 *         for (int y = 0; y &lt; range; ++y) {                // 16 次
 *             BlockPos bp = pos.offset(x, y - range / 2, z);
 *             list.add(new BlockData(bp, level.getBlockState(bp), null));   // ← 无条件全收
 *         }
 *     }
 * }
 * imb.processed(list);
 * </pre>
 *
 * 32 × 32 × 16 = <b>16384</b> 次 {@code getBlockState} +
 * <b>16384 个 {@code BlockData} 对象</b>（每个还内含一个 {@code BlockPos}），全在一个 tick 里。
 *
 * <h2>为什么这些对象几乎全是垃圾</h2>
 * 唯一的消费者是 {@code JobFarmer.processed}（反编译源码 314-336 行），它只关心三类：
 * <pre>
 * if (tile instanceof RandomizableContainerBlockEntity) { ... 找最近的箱子 ... }
 * if (!(b instanceof CropBlock) &amp;&amp; !(b instanceof StemBlock) || trackedBlocks.contains(data.pos)) continue;
 * trackedBlocks.add(data.pos);
 * </pre>
 * 一片农田里作物加箱子通常不到 100 个 —— 也就是 16384 个 {@code BlockData} 里
 * <b>99% 以上在创建后立刻被丢弃</b>，纯粹给 GC 制造工作。
 *
 * <h2>本类怎么做</h2>
 * 两件事，都不改变 {@code processed} 看到的结果：
 * <ol>
 *   <li><b>源头过滤。</b>扫描时就判断这个方块是否属于消费方关心的三类，
 *       不关心的<b>根本不创建 {@code BlockData}</b>。
 *       分配量从 16384 降到通常 &lt; 100。</li>
 *   <li><b>增量分片。</b>把扫描拆成跨多次调用的状态机
 *       （状态在 {@link MassBlockScanState}），每次最多扫
 *       {@link MassBlockScanState#scanPerCall()} 个坐标，扫完才回调 {@code processed}。
 *       单 tick 的 {@code getBlockState} 次数从 16384 降到 2048。</li>
 * </ol>
 *
 * <p>分摊的代价是延迟：{@code JobFarmer} 每 1200 tick（60 秒）请求一次扫描
 * （{@code aiShouldExecute} 第 159-163 行 {@code blockTicks++ > 1200}），
 * 16384 / 2048 = 8 次调用 × 21 tick = 168 tick ≈ 8.4 秒完成。
 * 相对 60 秒的周期完全够用，农夫的行为没有可观察差异。
 *
 * <h2>遍历顺序与原版一致</h2>
 * 原版三重循环的嵌套次序是 x（外）→ z（中）→ y（内）。
 * 本类把它拍平成一维游标，反算时保持同样的次序（见 {@code cnpcyouhua$incrementalScan}），
 * 所以 {@code collected} 的元素顺序与原版逐个相同。
 * 这一点很重要 —— {@code JobFarmer.processed} 把结果按顺序放进 {@code trackedBlocks}，
 * 而 {@code aiUpdateTask} 顺序遍历它找第一个成熟作物。顺序变了，农夫的行为就变了。
 *
 * <h2>过滤判据为什么这样写</h2>
 * 判据必须是消费方判据的<b>超集</b>，否则会丢数据。这里收四类：
 * <ul>
 *   <li>{@code CropBlock} —— {@code processed} 的作物判据</li>
 *   <li>{@code StemBlock} —— {@code processed} 的茎判据</li>
 *   <li>{@code StemGrownBlock} —— {@code aiUpdateTask}（285-295 行）复查
 *       {@code trackedBlocks} 时会检查它（南瓜/西瓜本体）。
 *       虽然 {@code processed} 不收它，但收进来是无害的（{@code processed} 自己会过滤掉），
 *       漏掉才可能出问题。宁可多收。</li>
 *   <li>有 {@code BlockEntity} 且是 {@code RandomizableContainerBlockEntity} —— 箱子判据</li>
 * </ul>
 *
 * <p><b>只对有 {@code BlockEntity} 的方块调 {@code getBlockEntity}</b>：
 * 先用 {@code state.hasBlockEntity()}（纯字段读）筛一遍，
 * 避免对 16384 个坐标都做一次区块 map 查找。
 * 原版的 {@code processed} 对<b>每一个</b> {@code BlockData} 都调了 {@code getBlockEntity}，
 * 这里连带把那 16384 次查找也省掉了。
 *
 * <h2>为什么保留「不过滤」的回退路径</h2>
 * {@code MassBlockController.IMassBlock} 是 CNPC 的公开接口，理论上第三方能实现。
 * 虽然它不在 {@code noppes.npcs.api} 包里（不属于脚本 API），
 * 且全仓库唯一实现就是 {@code JobFarmer}，
 * 但为了不给假想的第三方实现降低正确性，这里做运行时判断：
 * 只对 {@code JobFarmer} 走过滤路径，其他实现走「不过滤但仍分片」的路径 ——
 * 那样至少 TPS 尖峰被消掉了，而语义 100% 不变。
 */
@Mixin(value = MassBlockController.class, remap = false)
public abstract class MixinMassBlockController {

    @Inject(method = "Update", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcyouhua$incrementalScan(CallbackInfo ci) {
        if (MassBlockScanState.idle() && !cnpcyouhua$begin()) {
            // 队列空，且没有进行中的扫描。
            ci.cancel();
            return;
        }

        MassBlockController.IMassBlock imb = MassBlockScanState.current();
        Level level = MassBlockScanState.level();
        EntityNPCInterface npc = imb == null ? null : imb.getNpc();

        // 世界可能在扫描期间被卸载，NPC 可能被移除或换维度。
        if (imb == null || level == null || npc == null || npc.isRemoved() || npc.level() != level) {
            cnpcyouhua$finishEarly();
            ci.cancel();
            return;
        }

        int range = MassBlockScanState.range();
        int span = range * 2;          // x 与 z 各遍历 [-range, range)
        int perColumn = range;         // y 遍历 [0, range)
        int total = span * span * perColumn;

        int start = MassBlockScanState.cursor();
        int end = Math.min(start + MassBlockScanState.scanPerCall(), total);
        BlockPos origin = MassBlockScanState.origin();
        int halfRange = range / 2;
        boolean filtered = MassBlockScanState.filtered();

        for (int flat = start; flat < end; flat++) {
            // 反算成原版三重循环的 (x, z, y)。
            // 原版嵌套次序是 x（外）→ z（中）→ y（内），这里保持一致，
            // 使 collected 的元素顺序与原版逐个相同。
            int y = flat % perColumn;
            int column = flat / perColumn;
            int z = column % span - range;
            int x = column / span - range;

            if (y == 0) {
                // 原版每列开头做一次区块加载检查（第 45 行），y 固定为 64。
                // 只在列的第一个 y 上做，与原版频次一致。
                if (!level.hasChunkAt(new BlockPos(x + origin.getX(), 64, z + origin.getZ()))) {
                    // 整列跳过。游标直接推到下一列，省掉 range-1 次空转。
                    flat += perColumn - 1;
                    continue;
                }
            }

            BlockPos bp = origin.offset(x, y - halfRange, z);
            BlockState state = level.getBlockState(bp);

            if (filtered && !cnpcyouhua$wants(level, bp, state)) {
                continue;
            }
            MassBlockScanState.collect(new BlockData(bp, state, null));
        }
        MassBlockScanState.cursor(end);

        if (end >= total) {
            List<BlockData> result = MassBlockScanState.finish();
            imb.processed(result);
        }
        ci.cancel();
    }

    /**
     * 从队列取下一个请求者并初始化扫描状态。
     *
     * <p>队列是 {@code private static}，通过 {@link MassBlockControllerQueueAccess}
     * 的 Accessor 拿到 —— 见那个接口的说明。
     */
    @Unique
    private static boolean cnpcyouhua$begin() {
        Queue<MassBlockController.IMassBlock> queue = MassBlockControllerQueueAccess.cnpcyouhua$getQueue();
        if (queue == null || queue.isEmpty()) {
            return false;
        }
        MassBlockController.IMassBlock imb = queue.remove();
        EntityNPCInterface npc = imb.getNpc();
        if (npc == null || npc.isRemoved() || npc.level() == null) {
            // 请求者已失效。仍要回调空列表，否则 JobFarmer 会永远卡在
            // waitingForBlocks == true，再也不请求扫描。
            imb.processed(new ArrayList<>());
            return false;
        }
        int range = imb.getRange();
        if (range <= 0) {
            imb.processed(new ArrayList<>());
            return false;
        }

        // 只有已知消费判据的实现才启用过滤。
        // 未知的第三方实现保持原样（全收），只享受分片带来的 TPS 平滑。
        MassBlockScanState.begin(imb, npc.level(), npc.blockPosition(), range,
                imb instanceof JobFarmer);
        return true;
    }

    /** NPC 中途失效：回调空列表让请求方复位标志，然后清状态。 */
    @Unique
    private static void cnpcyouhua$finishEarly() {
        MassBlockController.IMassBlock done = MassBlockScanState.current();
        MassBlockScanState.reset();
        if (done != null) {
            done.processed(new ArrayList<>());
        }
    }

    /**
     * 这个方块会被 {@code JobFarmer.processed} / {@code aiUpdateTask} 用到吗？
     *
     * <p>判据是消费方判据的超集，见类注释。
     */
    @Unique
    private static boolean cnpcyouhua$wants(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock || block instanceof StemBlock || block instanceof StemGrownBlock) {
            return true;
        }
        // 先用纯字段读筛掉绝大多数方块，再做一次区块 map 查找。
        if (!state.hasBlockEntity()) {
            return false;
        }
        BlockEntity tile = level.getBlockEntity(pos);
        return tile instanceof RandomizableContainerBlockEntity;
    }
}
