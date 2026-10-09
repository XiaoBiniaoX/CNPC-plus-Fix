package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.perf.client.HighlightVersion;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 优化 #6 的核心：{@code TextContainer$LineData.getFormattedString()} 的记忆化。
 *
 * <h2>原版做了什么</h2>
 * {@code TextContainer.LineData.getFormattedString()}（反编译源码 23-42 行）：
 * <pre>
 * StringBuilder builder = new StringBuilder(this.text);
 * int found = 0;
 * for (TextContainer.MarkUp entry : TextContainer.this.makeup) {      // 遍历全文标记
 *     if (entry.start &gt;= this.start &amp;&amp; entry.start &lt; this.end) {
 *         builder.insert(entry.start - this.start + found * 2, MARK + entry.c);
 *         ++found;
 *     }
 *     if (entry.start &lt; this.start &amp;&amp; entry.end &gt; this.start) {
 *         builder.insert(0, MARK + entry.c);
 *         ++found;
 *     }
 *     if (entry.end &lt; this.start || entry.end &gt;= this.end) continue;
 *     builder.insert(entry.end - this.start + found * 2, MARK + 'r');
 *     ++found;
 * }
 * return builder.toString();
 * </pre>
 * （上面的 {@code MARK} 代表反编译源码里嵌入的 U+FFFF 控制字符字面量。）
 *
 * 调用方是 {@code GuiTextArea.render} 第 209 行 —— <b>每帧、每个可见行</b>调用一次。
 *
 * <h2>为什么「代码越多越卡」</h2>
 * 两层放大叠在一起：
 * <ol>
 *   <li><b>每一行都要遍历整份文件的标记表。</b>
 *       {@code TextContainer.this.makeup} 装的是整个脚本的高亮标记
 *       （数字、关键字、字符串、注释四类正则的全部匹配），<b>不是本行的</b>。
 *       一个 500 行的脚本轻松上千个标记。屏幕上 30 个可见行 × 上千个标记
 *       = 每帧数万次比较，全是重复劳动。</li>
 *   <li><b>{@code StringBuilder.insert} 是 O(n)。</b>
 *       每次中间插入都要 {@code System.arraycopy} 移动后半段。
 *       一行有 k 个标记就有 2k 次插入。</li>
 * </ol>
 *
 * <h2>关键观察：这是个纯函数</h2>
 * 输出只取决于 {@code this.text}、{@code this.start}/{@code this.end}
 * 以及 {@code makeup} 的内容。{@code LineData} 的三个字段构造后不再变，
 * 而 {@code GuiTextArea.setText}（549-569 行）改文本时会
 * {@code new TextContainer(text)} 造一个全新容器 ——
 * 旧的 {@code LineData} 连同它引用的 {@code makeup} 一起被整体丢弃。
 *
 * <p>所以只要 {@code makeup} 的内容没变，缓存就永久有效。
 *
 * <h2>失效判据：全局单调版本号</h2>
 * {@code LineData} 是<b>非静态内部类</b>，访问 {@code makeup} 要经过编译器合成的
 * {@code this$0} 字段 —— 那个名字由编译器决定，{@code @Shadow} 它是脆的。
 *
 * <p>所以改用一个不依赖内部结构的判据：
 * {@link MixinTextContainerInvalidate} 挂在 {@code makeup} 的<b>全部两个写入点</b>上，
 * 任一被调用就 {@link HighlightVersion#bump()}。本类只需比对版本号。
 *
 * <p><b>代价</b>：任意一个 {@code TextContainer} 的变化会让所有 {@code LineData}
 * 的缓存一起失效。这是刻意接受的折中 —— 同一时刻屏幕上只有一个
 * {@code GuiTextArea} 在编辑，而 {@code makeup} 只在玩家敲键时变化。
 * 换来的是完全不碰内部类合成字段，跨编译器/跨 CNPC 版本稳定。
 *
 * <p><b>正确性</b>：版本号只增不减，缓存条目记录「算这个结果时的版本」。
 * 版本一变，全部缓存立刻被判过期并重算。不存在读到脏数据的可能。
 *
 * <h2>为什么用 HEAD + RETURN 两个注入</h2>
 * HEAD 负责命中时提前返回（省时间的地方），RETURN 负责把新算的结果存下来。
 * 这样原方法体<b>一字不改</b> —— 高亮的实际生成逻辑还是 CNPC 自己的，
 * 我们只是在外面套了一层备忘录。
 * 任何其他附属如果也想改高亮算法，它的注入照常生效，我们缓存的就是它改过之后的结果。
 *
 * <h2>为什么 {@code @Mixin} 用 {@code targets} 而不是 {@code value}</h2>
 * {@code TextContainer$LineData} 是<b>包级私有</b>的内部类
 * （javap 确认：{@code class TextContainer$LineData}，无 {@code public} 修饰）。
 * 从 {@code bin.cnpcplus.mixin.perf} 包里引用不到它的 {@code Class} 字面量，
 * 所以必须用 {@code targets = "全限定名"} 的字符串形式。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(targets = "noppes.npcs.shared.client.gui.components.TextContainer$LineData", remap = false)
public abstract class MixinTextContainerLineData {

    /** 上次算出的格式化串。 */
    @Unique private String cnpcplus$cached;

    /** 算出 {@link #cnpcplus$cached} 时的全局标记版本号。 */
    @Unique private int cnpcplus$cachedVersion = Integer.MIN_VALUE;

    @Inject(method = "getFormattedString", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$memoized(CallbackInfoReturnable<String> cir) {
        if (this.cnpcplus$cached != null
                && this.cnpcplus$cachedVersion == HighlightVersion.current()) {
            cir.setReturnValue(this.cnpcplus$cached);
        }
    }

    @Inject(method = "getFormattedString", at = @At("RETURN"), remap = false)
    private void cnpcplus$remember(CallbackInfoReturnable<String> cir) {
        this.cnpcplus$cached = cir.getReturnValue();
        this.cnpcplus$cachedVersion = HighlightVersion.current();
    }
}
