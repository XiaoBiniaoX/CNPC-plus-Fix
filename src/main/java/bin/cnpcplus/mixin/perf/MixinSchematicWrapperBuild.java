package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import noppes.npcs.schematics.ISchematic;
import noppes.npcs.schematics.SchematicWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 优化 #1 的第三部分（1.20.1 主内容移植）：消除 {@code SchematicWrapper.build()}
 * 的双轮全量遍历与单批放置峰值。
 *
 * <h2>原版做了什么（javap 实证，1.12.2 05Jul20）</h2>
 * {@code SchematicController.updateBuilding()} 被 {@code ServerTickHandler.onServerTick}
 * 每 20 tick 调用一次（offset 31-42：{@code ticks++ >= 20} 门控），每次
 * {@code building.build()} 处理最多 <b>10000 个索引</b>：
 * <pre>
 * long endPos = this.buildPos + 10000;
 * while (this.buildPos &lt; endPos) {
 *     ... 由 buildPos 反算 x/y/z
 *     this.place(x, y, z, this.firstLayer ? 1 : 2);
 *     ++this.buildPos;
 * }
 * if (this.buildPos &gt;= this.size) {
 *     if (this.firstLayer) { this.firstLayer = false; this.buildPos = 0; }  // 归零再走一轮
 *     else this.isBuilding = false;
 * }
 * </pre>
 * {@code place(x,y,z,flag)} 的过滤（1.12.2 用无参 {@code isFullBlock()} = func_185913_b）：
 * <ul>
 *   <li>flag==1（第一轮）：{@code state.isFullBlock()} 为真，或 {@code state.getBlock() == AIR} 才放；</li>
 *   <li>flag==2（第二轮）：非实心<b>且</b>非空气才放。</li>
 * </ul>
 * 两轮判据互补——两轮加起来真实 {@code setBlock}（World.func_180501_a，flag 2）次数
 * 恰为 {@code size}，另 {@code size} 次索引纯属空转（仍要一次 getBlockState + isFullBlock）。
 *
 * <h2>本类怎么做</h2>
 * 双轮遍历改成<b>一次预筛 + 按需放置</b>：
 * <ul>
 *   <li>先扫 {@code [0, size)}，用与 place 完全相同的判据把索引分进两个 int[]，
 *       预筛只读不写世界，且本身也分摊（每次 build() 调用最多扫 {@link #SCAN_PER_BATCH} 个）；</li>
 *   <li>之后每次调用从预筛结果取最多 {@link #PLACE_PER_BATCH} 个索引真实 place，零空转。</li>
 * </ul>
 *
 * <p><b>放置顺序完全等价</b>：预筛保持索引升序，与原版 buildPos 从 0 递增一致；
 * 「先第一轮（实心/空气）再第二轮（非实心）」两阶段语义完整保留——
 * 楼梯、栅栏、门、红石的最终状态依赖放置顺序，这不是可选项。
 * {@code buildPos}/{@code firstLayer} 仍被正确维护，{@code getPercentage()} 公式不变。
 *
 * <p>调用频率不变（仍每 20 tick 一次），单批真实放置从约 5000 降到 2500（峰值约减半），
 * 总建造时长约翻倍——示意图建造本来就是后台过程，方块逐渐出现，慢几秒无感、卡顿有感。
 *
 * <h2>配置</h2>
 * {@code schematicBuildSpread}（默认 true）。关闭时 HEAD 注入直接放行，原版 10000 批运行。
 */
@Mixin(value = SchematicWrapper.class, remap = false)
public abstract class MixinSchematicWrapperBuild {

    /** 每次 build() 最多预筛多少个索引（只读，远便宜于 place）。 */
    @Unique
    private static final int SCAN_PER_BATCH = 20_000;

    /** 每次 build() 最多真实放置多少个方块。 */
    @Unique
    private static final int PLACE_PER_BATCH = 2_500;

    @Shadow
    public ISchematic schema;

    @Shadow
    public int buildPos;

    @Shadow
    public int size;

    @Shadow
    public boolean isBuilding;

    @Shadow
    public boolean firstLayer;

    @Shadow
    private World world;

    @Shadow
    public abstract void place(int x, int y, int z, int flag);

    /** 第一轮（实心或空气）待放索引，升序。 */
    @Unique
    private int[] cnpcplus$firstPass;

    @Unique
    private int cnpcplus$firstCount;

    @Unique
    private int cnpcplus$firstCursor;

    /** 第二轮（非实心且非空气）待放索引，升序。 */
    @Unique
    private int[] cnpcplus$secondPass;

    @Unique
    private int cnpcplus$secondCount;

    @Unique
    private int cnpcplus$secondCursor;

    @Unique
    private int cnpcplus$scanned;

    @Unique
    private boolean cnpcplus$prepared;

    @Inject(method = "build", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$spreadBuild(CallbackInfo ci) {
        if (!CnpcPlusConfig.isSchematicBuildSpreadEnabled()) {
            return;
        }
        // 与原版前两句逐条对应。
        if (this.world == null || !this.isBuilding) {
            ci.cancel();
            return;
        }

        if (!this.cnpcplus$prepared) {
            cnpcplus$scan();
            if (!this.cnpcplus$prepared) {
                // 预筛未完成：这一步零 setBlock，多花一次 20 tick 调用不影响 TPS。
                ci.cancel();
                return;
            }
        }

        cnpcplus$placeSome();
        ci.cancel();
    }

    /** 分摊式预筛：把 [0, size) 切片，每次调用处理一片。 */
    @Unique
    private void cnpcplus$scan() {
        if (this.cnpcplus$firstPass == null) {
            // 容量上界就是 size：最坏情况全部索引落进同一轮。
            int cap = Math.max(this.size, 1);
            this.cnpcplus$firstPass = new int[cap];
            this.cnpcplus$secondPass = new int[cap];
            this.cnpcplus$scanned = 0;
            this.cnpcplus$firstCount = 0;
            this.cnpcplus$secondCount = 0;
        }

        int width = this.schema.getWidth();
        int length = this.schema.getLength();
        int end = Math.min(this.cnpcplus$scanned + SCAN_PER_BATCH, this.size);

        for (int i = this.cnpcplus$scanned; i < end; i++) {
            int x = i % width;
            int z = (i - x) / width % length;
            int y = ((i - x) / width - z) / length;

            IBlockState state;
            try {
                state = this.schema.getBlockState(x, y, z);
            } catch (Exception e) {
                continue;
            }
            if (state == null) {
                // 原版 place() 第一句同样直接返回，两轮都不放。
                continue;
            }

            // 下面两个判据严格等于 place() 的「不 return」条件（1.12.2 无参 isFullBlock）。
            boolean solid = state.isFullBlock();
            Block block = state.getBlock();
            boolean air = block == Blocks.AIR;

            if (solid || air) {
                this.cnpcplus$firstPass[this.cnpcplus$firstCount++] = i;
            } else {
                this.cnpcplus$secondPass[this.cnpcplus$secondCount++] = i;
            }
        }
        this.cnpcplus$scanned = end;

        if (this.cnpcplus$scanned >= this.size) {
            this.cnpcplus$prepared = true;
            this.cnpcplus$firstCursor = 0;
            this.cnpcplus$secondCursor = 0;
        }
    }

    /** 从预筛结果放置至多 {@link #PLACE_PER_BATCH} 个方块。 */
    @Unique
    private void cnpcplus$placeSome() {
        int width = this.schema.getWidth();
        int length = this.schema.getLength();
        int placed = 0;

        // 第一阶段：实心或空气（对应原版 firstLayer == true, flag = 1）。
        while (this.firstLayer && placed < PLACE_PER_BATCH) {
            if (this.cnpcplus$firstCursor >= this.cnpcplus$firstCount) {
                // 第一轮结束，切第二轮。与原版 size 触底逻辑等价。
                this.firstLayer = false;
                this.buildPos = 0;
                break;
            }
            int i = this.cnpcplus$firstPass[this.cnpcplus$firstCursor++];
            int x = i % width;
            int z = (i - x) / width % length;
            int y = ((i - x) / width - z) / length;
            this.place(x, y, z, 1);
            // buildPos 跟随真实索引推进，getPercentage() 语义不变。
            this.buildPos = i + 1;
            placed++;
        }

        // 第二阶段：非实心且非空气（对应原版 firstLayer == false, flag = 2）。
        while (!this.firstLayer && placed < PLACE_PER_BATCH) {
            if (this.cnpcplus$secondCursor >= this.cnpcplus$secondCount) {
                // 全部放完。与原版收尾等价。
                this.buildPos = this.size;
                this.isBuilding = false;
                cnpcplus$release();
                return;
            }
            int i = this.cnpcplus$secondPass[this.cnpcplus$secondCursor++];
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
    private void cnpcplus$release() {
        this.cnpcplus$firstPass = null;
        this.cnpcplus$secondPass = null;
        this.cnpcplus$prepared = false;
        this.cnpcplus$scanned = 0;
        this.cnpcplus$firstCount = 0;
        this.cnpcplus$secondCount = 0;
    }
}
