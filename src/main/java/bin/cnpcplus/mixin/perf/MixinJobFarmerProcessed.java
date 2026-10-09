package bin.cnpcplus.mixin.perf;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import noppes.npcs.controllers.data.BlockData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobFarmer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 优化 #2 的配套：消除 {@code JobFarmer.processed} 里的 O(n²) 去重。
 *
 * <h2>原版做了什么</h2>
 * {@code JobFarmer.processed(List<BlockData>)}（反编译源码 314-336 行）：
 * <pre>
 * ArrayList&lt;BlockPos&gt; trackedBlocks = new ArrayList&lt;&gt;();
 * BlockPos chest = null;
 * for (BlockData data : list) {
 *     BlockEntity tile = npc.level().getBlockEntity(data.pos);   // ← 对每一个都查
 *     Block b = data.state.getBlock();
 *     if (tile instanceof RandomizableContainerBlockEntity) { ... 取最近的箱子 ... continue; }
 *     if (!(b instanceof CropBlock) &amp;&amp; !(b instanceof StemBlock)
 *             || trackedBlocks.contains(data.pos)) continue;          // ← O(n) 线性查找
 *     trackedBlocks.add(data.pos);
 * }
 * </pre>
 *
 * <h2>两个问题</h2>
 * <ol>
 *   <li><b>{@code trackedBlocks.contains} 是 {@code ArrayList} 的 O(n) 线性查找</b>，
 *       放在收集循环里就是 O(n²)。这里的 n 是农田里的作物数量，
 *       一片 32×32 的密集农田可以有上千株，n² 就是百万次 {@code BlockPos.equals}。</li>
 *   <li><b>对每个候选都调 {@code level.getBlockEntity(pos)}</b>。
 *       原版传进来的 list 有 16384 项（见 {@link MixinMassBlockController}），
 *       就是 16384 次区块 {@code BlockEntity} map 查找。</li>
 * </ol>
 *
 * <p>值得注意的是：{@link MixinMassBlockController} 的源头过滤已经把
 * list 的长度从 16384 压到通常 &lt; 100，所以第 2 点在优化 #2 生效后基本消失。
 * 但第 1 点仍然存在（去重是对作物做的，作物本来就是被保留的那部分），
 * 而且如果哪天有第三方 {@code IMassBlock} 实现走「不过滤」路径，第 2 点会回来。
 * 所以这里两个都修。
 *
 * <h2>本类怎么做</h2>
 * <ul>
 *   <li>去重容器从 {@code ArrayList} 换成 {@code HashSet}，O(n²) → O(n)。</li>
 *   <li>先用 {@code state.hasBlockEntity()}（纯字段读，无查找）筛一遍，
 *       只对可能有 BlockEntity 的方块调 {@code getBlockEntity}。</li>
 * </ul>
 *
 * <p><b>为什么结果完全等价</b>：
 * <ul>
 *   <li>{@code trackedBlocks} 最终交给 {@code this.trackedBlocks} 字段，
 *       被 {@code aiUpdateTask}（285-295 行）用 {@code Iterator} 顺序遍历。
 *       原版的 {@code ArrayList} 保持插入顺序，所以农夫会按「x 外层、z 中层、y 内层」的
 *       扫描顺序去找第一个成熟作物。为了不改变这个行为，
 *       这里<b>仍然把结果放进 {@code ArrayList}</b>（保序），
 *       {@code HashSet} 只用来做去重判断。</li>
 *   <li>箱子的选取逻辑（取距 NPC 最近的那个）一字不改。</li>
 *   <li>{@code waitingForBlocks = false} 的复位照旧 —— 漏掉这一句农夫会永久停止扫描。</li>
 * </ul>
 *
 * <p><b>1.21.1 已核签名一致</b>（javap）：
 * {@code JobFarmer} 在 1.21.1 位于 {@code noppes.npcs.roles.JobFarmer}
 * （不再是从前的 {@code noppes.npcs.jobs.JobFarmer}），
 * {@code public processed(List&lt;BlockData&gt;)} 存在，
 * {@code private trackedBlocks / chest / waitingForBlocks} 三个字段原样存在，
 * 方法体字节码与上面引用的 1.20.1 反编译逐句一致（先 getBlockEntity 判箱子取最近，
 * 再 Crop/Stem 判据 + List.contains 去重，最后三个字段一起写）。
 */
@Mixin(value = JobFarmer.class, remap = false)
public abstract class MixinJobFarmerProcessed {

    /*
     * 注意：{@code npc} 字段<b>不能</b>用 @Shadow。
     *
     * javap 实证它声明在<b>父类</b> {@code JobInterface} 上
     * （{@code public EntityNPCInterface npc;}），{@code JobFarmer} 自己没有这个字段。
     * 1.21.1 再次核实仍然如此：{@code noppes.npcs.roles.JobInterface.npc} 是 public 非 final。
     * Mixin 的 @Shadow 只在<b>目标类本身</b>查找成员，对继承来的字段会报
     * "Cannot find target for @Shadow field" —— 那是个警告而不是错误，
     * 但运行期该字段访问会指向错误位置或直接失效。
     *
     * 正确做法：{@code npc} 是 public 的，直接从 JobFarmer 实例上读即可，不需要 @Shadow。
     */

    @Shadow private List<BlockPos> trackedBlocks;
    @Shadow private BlockPos chest;
    @Shadow private boolean waitingForBlocks;

    @Inject(method = "processed", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$fastProcessed(List<BlockData> list, CallbackInfo ci) {
        // npc 声明在父类 JobInterface 上（public），不能 @Shadow，直接从实例读。
        EntityNPCInterface npc = ((JobFarmer) (Object) this).npc;
        if (npc == null || npc.level() == null) {
            // 极端情况（NPC 已被移除）。仍要复位标志，否则农夫永久停止扫描。
            this.waitingForBlocks = false;
            ci.cancel();
            return;
        }

        // ArrayList 保序（aiUpdateTask 依赖遍历顺序），HashSet 只做 O(1) 去重判断。
        List<BlockPos> tracked = new ArrayList<>(Math.min(list.size(), 256));
        Set<BlockPos> seen = new HashSet<>(Math.min(list.size() * 2, 512));
        BlockPos chest = null;

        for (int i = 0; i < list.size(); i++) {
            BlockData data = list.get(i);
            Block block = data.state.getBlock();

            // 原版对每个候选都无条件调 getBlockEntity。
            // hasBlockEntity() 是 BlockState 上的一个布尔字段读，用它先筛掉绝大多数。
            if (data.state.hasBlockEntity()) {
                BlockEntity tile = npc.level().getBlockEntity(data.pos);
                if (tile instanceof RandomizableContainerBlockEntity) {
                    // 与原版 328-331 行等价：保留距 NPC 最近的箱子。
                    if (chest == null
                            || npc.distanceToSqr(chest.getX(), chest.getY(), chest.getZ())
                            > npc.distanceToSqr(data.pos.getX(), data.pos.getY(), data.pos.getZ())) {
                        chest = data.pos;
                    }
                    continue;
                }
            }

            // 与原版第 333-334 行等价，只是 contains 换成 Set。
            if (!(block instanceof CropBlock) && !(block instanceof StemBlock)) {
                continue;
            }
            if (!seen.add(data.pos)) {
                continue;
            }
            tracked.add(data.pos);
        }

        this.chest = chest;
        this.trackedBlocks = tracked;
        // 这一句不能漏：不复位农夫会永远卡在 waitingForBlocks == true，再也不请求扫描。
        this.waitingForBlocks = false;
        ci.cancel();
    }
}
