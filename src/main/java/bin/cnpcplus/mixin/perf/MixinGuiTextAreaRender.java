package bin.cnpcplus.mixin.perf;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;

/**
 * 优化 #6 的第二部分：消除 {@code GuiTextArea.render} 里的每帧行列表拷贝。
 *
 * <h2>原版做了什么</h2>
 * {@code GuiTextArea.render}（反编译源码 101-217 行）第 149 行：
 * <pre>
 * ArrayList list = new ArrayList(this.container.lines);
 * </pre>
 * 然后第 158-216 行用 {@code list.get(i)} 遍历。
 *
 * <h2>问题</h2>
 * 这是一次<b>每帧</b>的完整列表拷贝。
 * {@code container.lines} 的长度是文件的<b>总行数</b>（不是可见行数）——
 * {@code TextContainer.init}（49-85 行）会为每一行（含自动折行产生的行）
 * 创建一个 {@code LineData} 并全部装进 {@code lines}。
 *
 * <p>一个 3000 行的脚本：每帧一次 3000 元素的 {@code Object[]} 分配（约 12 KB）
 * 加 3000 次引用拷贝。60 FPS 下就是 <b>720 KB/秒的纯垃圾</b>，
 * 全部是 Eden 区的短命对象 —— 直接推高 young GC 频率。
 *
 * <h2>为什么这个拷贝是不必要的</h2>
 * 拷贝的常见理由是「防止遍历期间集合被修改」。
 * 但 {@code container.lines} 在 {@code render} 执行期间不可能变化：
 * <ul>
 *   <li>{@code lines} 只在 {@code TextContainer.init} 里被填充，
 *       而 {@code init} 只由 {@code GuiTextArea.setText}（第 563 行）调用。</li>
 *   <li>{@code setText} 只在输入事件里被调用
 *       （{@code charTyped}、{@code keyPressed}、{@code addText}），
 *       那些是 Screen 的事件回调，与 {@code render} 在同一线程<b>串行</b>执行，
 *       不会交错。</li>
 *   <li>{@code render} 自身不调用 {@code setText}。它调用的
 *       {@code getSelectionPos} / {@code setCursor} / {@code font.draw} 都不改 {@code lines}。</li>
 *   <li>而且 {@code setText} 走的是 {@code this.container = new TextContainer(text)} ——
 *       换的是整个容器对象，连 {@code lines} 引用本身都是新的。
 *       就算真的在遍历中途发生，拿着旧引用遍历也是安全的（旧列表内容不变）。</li>
 * </ul>
 *
 * <p>所以直接遍历原列表在语义上完全等价。
 *
 * <h2>实现方式：重定向构造调用</h2>
 * 用 {@code @Redirect} 拦下 {@code new ArrayList(Collection)} 这条构造指令。
 * 处理函数在源集合本身就是 {@code ArrayList} 时直接返回它，零拷贝零分配。
 *
 * <p><b>为什么带 {@code require = 0}</b>：构造器重定向依赖字节码里
 * {@code NEW + DUP + INVOKESPECIAL} 的具体形态。如果将来 CNPC 换了写法
 * （比如改成 {@code List.copyOf} 或 {@code new ArrayList<>(n)} 再 addAll），
 * 这个注入会匹配不上。
 *
 * <p>对于一个「省一次拷贝」的优化，让它<b>静默失效</b>远好于让游戏崩溃 ——
 * {@code require = 0} 正是这个语义。其他 12 项优化不加这个标记，
 * 因为它们要么是正确性相关，要么注入点稳定（方法 HEAD/RETURN）。
 *
 * <p><b>为什么限定 {@code ordinal = 0}</b>：{@code GuiTextArea} 里有两处
 * 同样的 {@code new ArrayList(this.container.lines)} ——
 * {@code render} 第 149 行和 {@code getSelectionPos} 第 256 行。
 * 后者只在鼠标事件里调用（频率极低），没必要动，
 * 所以用 {@code method = "render"} 把作用域限死在 render 里。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiTextArea.class, remap = false)
public abstract class MixinGuiTextAreaRender {

    @Redirect(
            method = "render",
            at = @At(value = "NEW", target = "(Ljava/util/Collection;)Ljava/util/ArrayList;"),
            require = 0,
            remap = false
    )
    private ArrayList<?> cnpcplus$noPerFrameCopy(Collection<?> source) {
        if (source instanceof ArrayList<?> already) {
            // container.lines 在 TextContainer 里声明为 List 但实际是 ArrayList。
            // render 期间不会有人改它（见类注释的四条论证），直接用。
            return already;
        }
        // 万一将来换成别的 List 实现，退回原版行为。
        return new ArrayList<>(source);
    }
}
