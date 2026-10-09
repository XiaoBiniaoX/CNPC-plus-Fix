package bin.cnpcplus.mixin.perf;

import noppes.npcs.blocks.tiles.TileBuilder;
import noppes.npcs.schematics.SchematicWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Stack;

/**
 * 优化 #1 的服务端一半：消除 {@code TileBuilder.setSchematic} 的 O(n²) 建表。
 *
 * <h2>原版做了什么</h2>
 * {@code TileBuilder.setSchematic}（反编译源码 130-169 行）为蓝图的每个方块算一个线性索引，
 * 按「四象限 × 逐层」的顺序压进一个 {@code Stack<Integer>}：
 * <pre>
 * Stack&lt;Integer&gt; positions = new Stack&lt;&gt;();
 * for (y ...) {
 *     for (z = 0; z &lt; L/2; z++) for (x = 0; x &lt; W/2; x++) positions.add(0, xyzToIndex(x, y, z));
 *     for (z = 0; z &lt; L/2; z++) for (x = W/2; x &lt; W; x++) positions.add(0, xyzToIndex(x, y, z));
 *     for (z = L/2; z &lt; L; z++) for (x = 0; x &lt; W/2; x++) positions.add(0, xyzToIndex(x, y, z));
 *     for (z = L/2; z &lt; L; z++) for (x = W/2; x &lt; W; x++) positions.add(0, xyzToIndex(x, y, z));
 * }
 * </pre>
 *
 * <h2>为什么是 O(n²)</h2>
 * {@code Stack} 继承 {@code Vector}，{@code add(0, e)} 每次都要把已有的全部元素
 * 通过 {@code System.arraycopy} 往后搬一格。n 次头插的累计搬移量是 n(n-1)/2。
 *
 * <p>实测量级：100×20×100 的蓝图有 200000 个方块，累计搬移 2×10^10 个引用，
 * 是一次几十秒的<b>服务器完全冻结</b>；触发点仅仅是玩家在 GUI 里点选一个蓝图
 * （{@code SPacketSchematicsTileSet.handle} → {@code tile.setSchematic(...)}）。
 * 即使只有 32×16×32（16384 格）也要搬 1.3 亿次，肉眼可见的卡顿。
 *
 * <h2>本类怎么做</h2>
 * 全部改成尾部追加（{@code Vector.add(E)}，O(1) 摊还），最后整体 {@link Collections#reverse} 一次。
 *
 * <p><b>为什么反转之后语义完全等价</b>：
 * 「依次头部插入 a₁…aₙ」得到序列 ⟨aₙ, …, a₁⟩；
 * 「依次尾部追加 a₁…aₙ 再整体反转」也得到 ⟨aₙ, …, a₁⟩。
 * 这是恒等，不是近似。所以后续 {@code getBlock()} 里 {@code positions.pop()}
 * 弹出的次序、也就是 NPC 建造方块的先后顺序，与原版<b>逐个方块完全一致</b>。
 *
 * <p>复杂度 O(n²) → O(n)。外加 {@code ensureCapacity} 消除了 {@code Vector} 的反复扩容拷贝。
 *
 * <h2>为什么用 {@code @Inject(HEAD, cancellable)} 而不是 {@code @Overwrite}</h2>
 * 保留其他 CNPC 附属在同一方法上注入的可能。{@code @Overwrite} 会独占方法，
 * 任何同样想增强建筑方块的附属都会与本 mod 硬冲突。
 * 这里必须完整取代方法体（循环结构的重排无法用局部注入表达），
 * 所以做法是「在 HEAD 完整重算然后 {@code cancel()}」——
 * 语义等价于 Overwrite，但挂在 HEAD/RETURN 上的其他注入照常执行。
 *
 * <p><b>1.21.1 已核签名一致</b>（javap）：
 * {@code public setSchematic(SchematicWrapper)} 存在；
 * 方法体字节码与上面引用的 1.20.1 反编译<b>语句次序一致</b>：
 * 先赋值 {@code this.schematic = param}，再判空返回，
 * 然后取 {@code schema} 宽/高/长、四象限头插循环、
 * 末尾 {@code positions = newStack; positionsSecond.clear()}。
 * {@code private Stack&lt;Integer&gt; positions/positionsSecond}、
 * {@code private SchematicWrapper schematic}、{@code public int xyzToIndex(int,int,int)}
 * 全部存在。
 */
@Mixin(value = TileBuilder.class, remap = false)
public abstract class MixinTileBuilderPositions {

    /**
     * {@code TileBuilder} 的三个 private 字段，通过 {@code @Shadow} 直连。
     *
     * <p>这几个字段属于 CNPC 自有类，名字不经过 SRG 重映射
     * （{@code remap = false} 的含义就是「目标成员名照字面用」），
     * 所以不存在「@Shadow 没进 refmap 导致生产环境找不到目标」的问题 ——
     * 那条陷阱只适用于 Minecraft 自身的成员。
     */
    @Shadow private SchematicWrapper schematic;
    @Shadow private Stack<Integer> positions;
    @Shadow private Stack<Integer> positionsSecond;

    /** CNPC 自有方法，签名 {@code (III)I}。索引算法依赖 {@link #schematic}，所以必须先赋值再调用。 */
    @Shadow public abstract int xyzToIndex(int x, int y, int z);

    @Inject(method = "setSchematic", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$linearBuildOrder(SchematicWrapper schematics, CallbackInfo ci) {
        // 原版第 131 行先赋值，第 132-136 行才判空。这里保持同样的次序，
        // 因为 setSchematic(null) 的语义是「清空蓝图」，字段必须被置 null。
        // 1.21.1 字节码已核实：仍是「先赋值、再判空」。
        this.schematic = schematics;

        if (schematics == null) {
            this.positions.clear();
            this.positionsSecond.clear();
            ci.cancel();
            return;
        }

        int width = schematics.schema.getWidth();
        int height = schematics.schema.getHeight();
        int length = schematics.schema.getLength();
        int halfW = width / 2;
        int halfL = length / 2;

        Stack<Integer> ordered = new Stack<>();
        int total = width * height * length;
        if (total > 0) {
            ordered.ensureCapacity(total);
        }

        // 下面四个内层循环的边界与遍历次序，与原版 147-166 行逐行一一对应。
        // 唯一的差别是 add(0, v) 换成了 add(v)，由末尾的 reverse 补偿。
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < halfL; z++) {
                for (int x = 0; x < halfW; x++) {
                    ordered.add(this.xyzToIndex(x, y, z));
                }
            }
            for (int z = 0; z < halfL; z++) {
                for (int x = halfW; x < width; x++) {
                    ordered.add(this.xyzToIndex(x, y, z));
                }
            }
            for (int z = halfL; z < length; z++) {
                for (int x = 0; x < halfW; x++) {
                    ordered.add(this.xyzToIndex(x, y, z));
                }
            }
            for (int z = halfL; z < length; z++) {
                for (int x = halfW; x < width; x++) {
                    ordered.add(this.xyzToIndex(x, y, z));
                }
            }
        }

        Collections.reverse(ordered);

        this.positions = ordered;
        this.positionsSecond.clear();
        ci.cancel();
    }
}
