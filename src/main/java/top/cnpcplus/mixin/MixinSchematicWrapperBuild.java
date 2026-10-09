package top.cnpcplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.schematics.ISchematic;
import noppes.npcs.schematics.SchematicWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 优化 #1 的第三部分：消除 {@code SchematicWrapper.build()} 的双轮全量遍历与单 tick 峰值。
 *
 * <h2>原版做了什么</h2>
 * {@code SchematicWrapper.build()}（反编译源码 95-122 行）被
 * {@code SchematicController.updateBuilding()} 每 21 tick 调用一次
 * （{@code ServerTickHandler.onServerTick} 第 105-107 行的 {@code this.ticks++ < 20} 门控），
 * 每次在<b>单个 tick 内</b>处理 10000 个索引：
 * <pre>
 * long endPos = this.buildPos + 10000;
 * while (this.buildPos &lt; endPos) {
 *     ... 由 buildPos 反算 x/y/z
 *     this.place(x, y, z, this.firstLayer ? 1 : 2);
 *     ++this.buildPos;
 * }
 * if (this.buildPos &gt;= this.size) {
 *     if (this.firstLayer) { this.firstLayer = false; this.buildPos = 0; }   // ← 归零，再走一整轮
 *     else this.isBuilding = false;
 * }
 * </pre>
 *
 * 而 {@code place(x, y, z, flag)}（124-146 行）的头几句是：
 * <pre>
 * BlockState state = this.schema.getBlockState(x, y, z);
 * if (state == null) return;
 * if (flag == 1 &amp;&amp; !state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
 *     if (state.getBlock() != Blocks.AIR) return;                 // ← 第一轮：非实心且非空气 → 跳过
 * }
 * if (flag == 2) {
 *     if (state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return;   // ← 第二轮：实心 → 跳过
 *     if (state.getBlock() == Blocks.AIR) return;                                  // ← 第二轮：空气 → 跳过
 * }
 * </pre>
 *
 * <h2>问题在哪</h2>
 * <ol>
 *   <li><b>完整遍历两轮，一半是空转。</b>
 *       第一轮只放「实心或空气」，第二轮只放「非实心且非空气」——
 *       两轮的判据互补，所以两轮加起来实际 {@code setBlock} 次数就是 {@code size}，
 *       另外 {@code size} 次纯属空转。空转也不便宜：每次都要一次
 *       {@code getBlockState} 加一次 {@code isSolidRender}。</li>
 *   <li><b>单 tick 峰值。</b>10000 次 {@code level.setBlock(pos, state, 2)} 挤在一个 tick 里。
 *       {@code setBlock} 会触发 section 标脏、光照更新入队、邻居形状更新、客户端同步 ——
 *       这是 TPS 尖峰的直接来源。</li>
 * </ol>
 *
 * <h2>本类怎么做</h2>
 * 双轮遍历改成<b>一次预筛 + 按需放置</b>：
 * <ul>
 *   <li>先扫一遍 {@code [0, size)}，用与 {@code place} 完全相同的判据把索引分进两个 {@code int[]}。
 *       预筛只读不写世界，且<b>本身也分摊</b>（每次调用最多扫 {@link #SCAN_PER_TICK} 个）。</li>
 *   <li>之后每次调用从预筛结果取 {@link #PLACE_PER_TICK} 个索引来放，<b>零空转</b>。</li>
 * </ul>
 *
 * <p>用 {@code int[]} 而不是 {@code List<Integer>}：后者会产生 {@code size} 个装箱对象，
 * 与「降低 GC 压力」的目标相悖。
 *
 * <p><b>放置顺序完全等价</b>：预筛保持索引升序，与原版 {@code buildPos} 从 0 递增一致；
 * 「先第一轮（实心/空气）再第二轮（非实心）」的两阶段语义完整保留。
 * 方块落地的先后次序与原版逐个一致 —— 这不是可选项，
 * 楼梯、栅栏、门、红石这类方块的最终状态依赖放置顺序。
 *
 * <h2>为什么 PLACE_PER_TICK 取 2500</h2>
 * 原版的 10000 是「10000 个索引」，其中约一半空转，实际 {@code setBlock} 约 5000。
 * 这里 2500 是「2500 次真实 {@code setBlock}」，峰值降到约 1/2。
 *
 * <p>代价是建造总时长变长：16384 格的蓝图原版约 4 次调用（84 tick ≈ 4.2 秒），
 * 现在约 7 次（147 tick ≈ 7.4 秒）。「示意图建造」本来就是后台过程，
 * 玩家看到的是方块逐渐出现 —— 慢 3 秒无感，卡顿有感。
 *
 * <p>{@code getPercentage()} 继续可用：{@code buildPos} 与 {@code firstLayer}
 * 仍被正确维护，百分比公式（{@code (buildPos + (firstLayer ? 0 : size)) / size * 50}）不变。
 */
@Mixin(value = SchematicWrapper.class, remap = false)
public abstract class MixinSchematicWrapperBuild {

    /** 每次 build() 最多预筛多少个索引。预筛不写世界，成本远低于 setBlock。 */
    @Unique private static final int SCAN_PER_TICK = 20_000;

    /** 每次 build() 最多真实放置多少个方块。 */
    @Unique private static final int PLACE_PER_TICK = 2_500;

    @Shadow public ISchematic schema;
    @Shadow public int buildPos;
    @Shadow public int size;
    @Shadow public boolean isBuilding;
    @Shadow public boolean firstLayer;
    @Shadow private Level level;

    @Shadow public abstract void place(int x, int y, int z, int flag);

    /** 第一轮（实心或空气）待放索引，升序。 */
    @Unique private int[] cnpcyouhua$firstPass;
    @Unique private int cnpcyouhua$firstCount;
    @Unique private int cnpcyouhua$firstCursor;

    /** 第二轮（非实心且非空气）待放索引，升序。 */
    @Unique private int[] cnpcyouhua$secondPass;
    @Unique private int cnpcyouhua$secondCount;
    @Unique private int cnpcyouhua$secondCursor;

    /** 预筛进度；达到 size 表示预筛完成。 */
    @Unique private int cnpcyouhua$scanned;
    @Unique private boolean cnpcyouhua$prepared;

    @Inject(method = "build", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcyouhua$spreadBuild(CallbackInfo ci) {
        // 与原版 96-99 行逐条对应。
        if (this.level == null || !this.isBuilding) {
            ci.cancel();
            return;
        }

        if (!this.cnpcyouhua$prepared) {
            cnpcyouhua$scan();
            if (!this.cnpcyouhua$prepared) {
                // 预筛未完成。这一步零 setBlock，多花几次调用不影响 TPS。
                ci.cancel();
                return;
            }
        }

        cnpcyouhua$placeSome();
        ci.cancel();
    }

    /** 分摊式预筛：把 [0, size) 切片，每次调用处理一片。 */
    @Unique
    private void cnpcyouhua$scan() {
        if (this.cnpcyouhua$firstPass == null) {
            // 容量上界就是 size：最坏情况全部索引落进同一轮。
            int cap = Math.max(this.size, 1);
            this.cnpcyouhua$firstPass = new int[cap];
            this.cnpcyouhua$secondPass = new int[cap];
            this.cnpcyouhua$scanned = 0;
            this.cnpcyouhua$firstCount = 0;
            this.cnpcyouhua$secondCount = 0;
        }

        int width = this.schema.getWidth();
        int length = this.schema.getLength();
        int end = Math.min(this.cnpcyouhua$scanned + SCAN_PER_TICK, this.size);

        for (int i = this.cnpcyouhua$scanned; i < end; i++) {
            int x = i % width;
            int z = (i - x) / width % length;
            int y = ((i - x) / width - z) / length;

            BlockState state;
            try {
                state = this.schema.getBlockState(x, y, z);
            } catch (Exception e) {
                continue;
            }
            if (state == null) {
                // 原版 place() 第 126 行同样直接返回，两轮都不放。
                continue;
            }

            // 下面两个判据严格等于 place() 第 127-135 行的「不 return」条件。
            boolean solid = state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            boolean air = state.getBlock() == Blocks.AIR;

            if (solid || air) {
                this.cnpcyouhua$firstPass[this.cnpcyouhua$firstCount++] = i;
            } else {
                this.cnpcyouhua$secondPass[this.cnpcyouhua$secondCount++] = i;
            }
        }
        this.cnpcyouhua$scanned = end;

        if (this.cnpcyouhua$scanned >= this.size) {
            this.cnpcyouhua$prepared = true;
            this.cnpcyouhua$firstCursor = 0;
            this.cnpcyouhua$secondCursor = 0;
        }
    }

    /** 从预筛结果放置至多 {@link #PLACE_PER_TICK} 个方块。 */
    @Unique
    private void cnpcyouhua$placeSome() {
        int width = this.schema.getWidth();
        int length = this.schema.getLength();
        int placed = 0;

        // 第一阶段：实心或空气（对应原版 firstLayer == true, flag = 1）
        while (this.firstLayer && placed < PLACE_PER_TICK) {
            if (this.cnpcyouhua$firstCursor >= this.cnpcyouhua$firstCount) {
                // 第一轮结束，切第二轮。与原版 116-118 行等价。
                this.firstLayer = false;
                this.buildPos = 0;
                break;
            }
            int i = this.cnpcyouhua$firstPass[this.cnpcyouhua$firstCursor++];
            int x = i % width;
            int z = (i - x) / width % length;
            int y = ((i - x) / width - z) / length;
            this.place(x, y, z, 1);
            // buildPos 跟随真实索引推进，getPercentage() 的语义不变。
            this.buildPos = i + 1;
            placed++;
        }

        // 第二阶段：非实心且非空气（对应原版 firstLayer == false, flag = 2）
        while (!this.firstLayer && placed < PLACE_PER_TICK) {
            if (this.cnpcyouhua$secondCursor >= this.cnpcyouhua$secondCount) {
                // 全部放完。与原版第 120 行等价。
                this.buildPos = this.size;
                this.isBuilding = false;
                cnpcyouhua$release();
                return;
            }
            int i = this.cnpcyouhua$secondPass[this.cnpcyouhua$secondCursor++];
            int x = i % width;
            int z = (i - x) / width % length;
            int y = ((i - x) / width - z) / length;
            this.place(x, y, z, 2);
            this.buildPos = i + 1;
            placed++;
        }
    }

    /** 建造完成后放掉两个 int[]（200000 格蓝图约 1.6 MB）。 */
    @Unique
    private void cnpcyouhua$release() {
        this.cnpcyouhua$firstPass = null;
        this.cnpcyouhua$secondPass = null;
        this.cnpcyouhua$prepared = false;
        this.cnpcyouhua$scanned = 0;
        this.cnpcyouhua$firstCount = 0;
        this.cnpcyouhua$secondCount = 0;
    }
}
