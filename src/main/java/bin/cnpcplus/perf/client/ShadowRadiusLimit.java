package bin.cnpcplus.perf.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 优化 #14：NPC 阴影半径上限。
 *
 * <h2>为什么需要这个（实机复现的掉帧根因）</h2>
 * 用恶魂之类的大体型实体作替身、并把 Size 拉满时，帧数会迅速掉下来，
 * 而且<b>删掉 NPC / 走远 / 改小 Size 都不恢复</b>。
 *
 * <p>根因在 vanilla 的 {@code EntityRenderDispatcher.renderShadow}：
 * 它是一个 <b>O(f² × 高度)</b> 的三重循环，{@code f} 就是渲染器的
 * {@code shadowRadius}：
 * <pre>
 * int i  = floor(x - f);  int j  = floor(x + f);      // X 跨度 2f
 * int i1 = floor(z - f);  int j1 = floor(z + f);      // Z 跨度 2f
 * for (k1 = i1; k1 &lt;= j1; ++k1)          // 2f 次
 *     for (l1 = i; l1 &lt;= j; ++l1) {      // 2f 次
 *         ChunkAccess chunk = level.getChunk(mutablePos);   // 每格一次区块查找
 *         for (i2 = k; i2 &lt;= l; ++i2)    // 高度跨度
 *             renderBlockShadow(...);     // 每格 4 个顶点
 *     }
 * </pre>
 *
 * <p>而 {@code RenderNPCInterface.render}（1.21.1 字节码偏移 333）：
 * <pre>
 * this.shadowRadius = npc.getBbWidth() * 0.8f;
 * </pre>
 *
 * <p>恶魂碰撞箱宽 4.0，Size 拉满（10）时 {@code EntityCustomNpc.getDimensions} 算出
 * {@code width = 4.0 / 5 * 10 = 8.0} → {@code shadowRadius = 6.4}。
 * 循环规模 {@code 13 × 13 × 高度}，每帧上千次 {@code getChunk} 加上万顶点。
 *
 * <p><b>而如果同时装了商人多页附属（{@code top.cnpcplus}），情况更糟</b>：
 * 它的 {@code MixinRenderNPCInterfaceShadow} 把这个值<b>放大</b>了 ——
 * {@code getBbWidth() * (display.getSize() / 5.0f)}，Size 拉满时
 * {@code 8.0 × 2.0 = 16.0}，再 {@code × 0.8} = <b>12.8</b>，
 * 循环规模 {@code 25 × 25 × 高度}。这是掉帧的直接放大器。
 *
 * <h2>为什么「不释放」</h2>
 * {@code shadowRadius} 是 {@code EntityRenderer} 的<b>实例字段</b>，
 * 而 Minecraft 的渲染器是<b>按实体类型单例</b>的 ——
 * 所有 {@code EntityCustomNpc} 共用同一个 {@code RenderCustomNpc}。
 *
 * <p>{@code render} 每帧把它设成「当前这个 NPC」的宽度，但<b>从不复原</b>。
 * 所以那个巨型恶魂 NPC 只要被渲染过一帧，{@code shadowRadius} 就停在 6.4（或 12.8），
 * 之后<b>每一个普通 NPC</b>（哪怕 Size 正常）都会用这个巨大的半径画阴影。
 * 把巨型 NPC 删掉也没用 —— 最后一次写入的值留在那里，
 * 直到下一个 NPC 渲染时才被覆盖，而那个值又取决于渲染顺序。
 * 这就是「触发后世界一直卡顿」的机制。
 *
 * <h2>上限取值的依据</h2>
 * vanilla 的参照：
 * <ul>
 *   <li>玩家 / 大部分怪物：{@code shadowRadius = 0.5}</li>
 *   <li>铁傀儡：0.7；末影龙、凋灵：<b>0</b>（大体型实体干脆不画阴影）</li>
 *   <li><b>恶魂自己的 vanilla 渲染器也是 0</b>（{@code GhastRenderer} 没设过 shadowRadius）</li>
 * </ul>
 *
 * <p>默认上限 {@link #DEFAULT_MAX} = 2.0：比任何 vanilla 实体都大（够让「大 NPC 有大阴影」
 * 的观感成立），同时把三重循环钳在 {@code 5 × 5 × 高度} —— 与普通实体同一量级。
 *
 * <p><b>碰撞箱完全不受影响</b>。这里只动渲染器的阴影半径字段，
 * {@code EntityCustomNpc.getDimensions} 返回的 {@code EntityDimensions} 一个字节都没碰。
 *
 * <h2>关于我先前的一次误判（记录以免重犯）</h2>
 * 我一开始怀疑的是 {@code EntityCustomNpc.getDimensions}（1.20.1 反编译第 137-139 行）的
 * {@code level().increaseMaxEntityRadius(width / 2.0f)} —— 那个值单调递增、永不复原，
 * 看起来非常像「不释放」的元凶。
 *
 * <p>但我把整个 NeoForge 1.21.1 源码树搜了一遍：
 * {@code getMaxEntityRadius} <b>只有定义和 getter/setter 自己，没有任何消费点</b>。
 * 它是 1.7 时代的遗留 API；实体查询早就改用 section 存储了，
 * {@code EntitySectionStorage.forEachAccessibleNonEmptySection} 用的是
 * <b>硬编码的 ±2.0D</b>，与 {@code maxEntityRadius} 无关。
 * 所以那条不是原因，真正的原因是上面这个阴影半径。
 */
@OnlyIn(Dist.CLIENT)
public final class ShadowRadiusLimit {

    /** 默认上限。见类注释里的取值依据。 */
    public static final float DEFAULT_MAX = 999.0f;

    /**
     * 当前上限。
     *
     * <p>设为 {@code 0} 可让所有 NPC 都不画阴影（与 vanilla 的末影龙/凋灵/恶魂一致）。
     * 设为很大的值（如 {@code 999}）等于关闭本项优化，退回原版行为。
     */
    private static volatile float max = DEFAULT_MAX;

    private ShadowRadiusLimit() {
    }

    /**
     * 钳制阴影半径。
     *
     * @param raw 原本要写入 {@code shadowRadius} 的值
     * @return 钳制后的值；{@code raw} 为 NaN/负数时返回 0（防御 vanilla 的
     *         {@code shadowRadius > 0.0F} 判断被 NaN 绕过）
     */
    public static float clamp(float raw) {
        if (Float.isNaN(raw) || raw <= 0.0f) {
            return 0.0f;
        }
        return Math.min(raw, max);
    }

    public static float getMax() {
        return max;
    }

    /** 由配置加载时调用。负值会被夹到 0。 */
    public static void setMax(float value) {
        max = Float.isNaN(value) ? DEFAULT_MAX : Math.max(0.0f, value);
    }
}
