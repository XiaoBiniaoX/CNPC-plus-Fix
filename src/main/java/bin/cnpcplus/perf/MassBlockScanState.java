package bin.cnpcplus.perf;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import noppes.npcs.controllers.MassBlockController;
import noppes.npcs.controllers.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * 优化 #2 的扫描状态机。
 *
 * <h2>为什么状态放在这个普通类里，不放在 Mixin 类的 {@code @Unique static} 字段里</h2>
 * Mixin 会把 mixin 类的成员<b>拷贝进目标类</b>。
 * 如果把 {@code current} / {@code cursor} / {@code collected} 写成
 * {@code MixinMassBlockController} 的 {@code @Unique private static} 字段，
 * 它们实际会成为 {@code MassBlockController} 的静态字段 ——
 * 这本身是可以工作的（同一个 mixin 内部读写指向同一份拷贝）。
 *
 * <p>但那样做有两个实际问题：
 * <ul>
 *   <li>调试时这些字段挂在 CNPC 的类上，日志和堆转储里看不出是谁加的。</li>
 *   <li>一旦将来需要从别的地方读这个状态（比如加个 {@code /cnpcplus status} 指令），
 *       就会踩上「按名字调 mixin 类静态成员，读到的是 mixin 类自己那份」的陷阱
 *       —— 那个陷阱在 {@code HighlightVersion} 的注释里详细写了。</li>
 * </ul>
 *
 * <p>放在普通类里两个问题都不存在，且 mixin 只剩下一层薄薄的转发。
 *
 * <h2>这个状态机做什么</h2>
 * 把原版 {@code MassBlockController.Update()} 的「单 tick 扫 16384 个坐标」
 * 拆成跨多次调用的增量扫描，并在扫描时就过滤掉消费方不关心的方块。
 * 详细的原版行为分析见 {@link bin.cnpcplus.mixin.perf.MixinMassBlockController} 的类注释。
 */
public final class MassBlockScanState {

    /** 每次 {@code Update()} 最多扫多少个坐标。16384 / 2048 = 8 次调用完成一轮。 */
    private static final int SCAN_PER_CALL = 2048;

    private static MassBlockController.IMassBlock current;
    private static Level level;
    private static BlockPos origin;
    private static int range;

    /** 三重循环拍平后的一维游标。 */
    private static int cursor;

    /** 本轮收集到的结果。只装真正会被消费的方块。 */
    private static List<BlockData> collected;

    /** 当前请求者是否启用过滤（已知消费判据的实现才启用）。 */
    private static boolean filtered;

    private MassBlockScanState() {
    }

    public static boolean idle() {
        return current == null;
    }

    public static MassBlockController.IMassBlock current() {
        return current;
    }

    public static Level level() {
        return level;
    }

    public static BlockPos origin() {
        return origin;
    }

    public static int range() {
        return range;
    }

    public static int cursor() {
        return cursor;
    }

    public static void cursor(int value) {
        cursor = value;
    }

    public static boolean filtered() {
        return filtered;
    }

    public static int scanPerCall() {
        return SCAN_PER_CALL;
    }

    public static void collect(BlockData data) {
        collected.add(data);
    }

    /** 开始扫描一个请求者。 */
    public static void begin(MassBlockController.IMassBlock imb, Level lvl, BlockPos pos,
                             int r, boolean useFilter) {
        current = imb;
        level = lvl;
        origin = pos;
        range = r;
        cursor = 0;
        collected = new ArrayList<>(256);
        filtered = useFilter;
    }

    /** 取出结果并清空状态。调用方负责把结果交给 {@code processed}。 */
    public static List<BlockData> finish() {
        List<BlockData> result = collected;
        reset();
        return result == null ? new ArrayList<>() : result;
    }

    public static void reset() {
        current = null;
        level = null;
        origin = null;
        range = 0;
        cursor = 0;
        collected = null;
        filtered = false;
    }

    /** 服务器停止时清空，避免跨存档残留。 */
    public static void clear() {
        reset();
    }
}
