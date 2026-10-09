package bin.cnpcplus.perf.client;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code Font.width(FormattedText)} 结果缓存（名牌路径专用）。
 *
 * <h2>为什么需要（1.21.1 字节码实证）</h2>
 * {@code RenderNPCInterface.renderLivingLabel} 每帧对<b>同一个</b>标题 Component 调
 * 两次 {@code Font.width}（SEE_THROUGH 与 NORMAL 两遍绘制各一次，偏移 240/289），
 * 对名字 Component 同样两次（偏移 347/397）。{@code Font.width} 内部走
 * {@code StringSplitter} 做<b>完整文本布局</b>（逐字符迭代 + 样式解析），
 * 比构造 {@code Component} 本身贵得多 —— 名牌是静态内容，这全是重复计算。
 * 每个可见名牌每帧 4 次全量布局，几十个 NPC 时开销显著（关掉名字显示
 * FPS 立刻回升的原因就在这条路径上）。
 *
 * <h2>正确性</h2>
 * 键用 {@link FormattedText} 本身：{@code Component} 的 equals/hashCode 是
 * 内容深度的，所以「每帧 new 出来的等价名字 Component」也能命中。
 * {@link WeakHashMap}（非线程安全——所有访问都在渲染线程，与
 * {@code renderLivingLabel} 的调用域一致）在键被 GC 后自动移除条目，
 * 不会滞留废弃 Component。
 *
 * <p><b>失效时机</b>：宽度依赖 Font 状态（资源重载/字体变更会重建
 * {@code StringSplitter}）。{@link RenderResourceCaches#flushReset()}
 * 在 F3+T 等资源重载时调用 {@link #clear()}。
 *
 * <p>容量防御：超过 {@link #MAX_ENTRIES} 直接整体清空（退化为重算，
 * 行为不变），防止异常场景下的无界增长。
 */
public final class FontWidthCache {

    private static final int MAX_ENTRIES = 4096;

    private static final Map<FormattedText, Integer> CACHE = new WeakHashMap<>(64);

    private FontWidthCache() {
    }

    /**
     * 取得 {@code text} 在 {@code font} 下的渲染宽度。
     *
     * <p>本缓存假设调用域内 {@code font} 恒为同一实例（实体名牌用
     * {@code Minecraft} 主字体，见 {@code RenderNPCInterface.getFont}）。
     * 若将来出现多字体混用，键需扩展为 (font, text)。
     */
    public static int width(Font font, FormattedText text) {
        Integer cached = CACHE.get(text);
        if (cached != null) {
            return cached;
        }
        int computed = font.width(text);
        if (CACHE.size() >= MAX_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(text, computed);
        return computed;
    }

    /** 资源重载/字体变更时由 {@link RenderResourceCaches#flushReset()} 调用。 */
    public static void clear() {
        CACHE.clear();
    }
}
