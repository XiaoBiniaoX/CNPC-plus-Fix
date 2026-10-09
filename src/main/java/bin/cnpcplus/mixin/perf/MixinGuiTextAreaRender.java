package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import noppes.npcs.client.gui.util.GuiTextArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;

/**
 * 优化 #6 的第二部分（1.20.1 主内容移植）：消除 {@code GuiTextArea.drawScreen} 的每帧行列表拷贝。
 *
 * <h2>原版做了什么（javap 实证，1.12.2 05Jul20）</h2>
 * <pre>
 * drawScreen:
 * 505: new java/util/ArrayList
 * 510: getfield container:TextContainer
 * 513: getfield TextContainer.lines:List
 * 516: invokespecial ArrayList.&lt;init&gt;(Collection)   ← 每帧完整拷贝总行数
 * 519: astore 5
 * </pre>
 * {@code container.lines} 是文件<b>总行数</b>（含自动折行）。3000 行脚本：
 * 每帧一次 3000 元素数组分配 + 3000 次引用拷贝，60 FPS 下约 720 KB/秒纯垃圾。
 *
 * <h2>为什么这个拷贝不必要</h2>
 * 拷贝通常为了防遍历中被修改。但 var5 在 drawScreen 后续字节码里
 * <b>只有读取</b>（javap 全段检索无任何 List.add/remove/clear/set）；
 * 而 {@code lines} 只在 TextContainer.init 里填充，init 只由 setText 调用，
 * setText 与 drawScreen 同线程串行（Screen 事件回调），不会交错；
 * 且 setText 走 {@code this.container = new TextContainer(text)}——换整个容器对象，
 * 连 lines 引用本身都是新的，拿旧引用遍历旧列表也安全。
 *
 * <h2>实现</h2>
 * {@code @Redirect} 拦下 {@code new ArrayList(Collection)} 构造指令：
 * 源集合本身是 ArrayList（lines 的实际类型）时直接返回它，零拷贝零分配。
 *
 * <p>{@code require = 0}：构造器重定向依赖 NEW+DUP+INVOKESPECIAL 具体形态，
 * 将来 CNPC 换写法就静默退回原版——对「省一次拷贝」的优化，静默失效远好于崩溃。
 * 作用域限定 drawScreen（getSelectionPos 里另有一处同样的拷贝，只在鼠标事件里跑，不动）。
 *
 * <h2>配置</h2>
 * {@code textRenderOptimize}（默认 true）。关闭时处理器照旧 new 一份。
 */
@Mixin(value = GuiTextArea.class, remap = false)
public abstract class MixinGuiTextAreaRender {

    @Redirect(
            method = "drawScreen",
            at = @At(value = "NEW", target = "(Ljava/util/Collection;)Ljava/util/ArrayList;"),
            require = 0,
            remap = false
    )
    private ArrayList<?> cnpcplus$noPerFrameCopy(Collection<?> source) {
        if (!CnpcPlusConfig.isTextRenderOptimizeEnabled()) {
            return new ArrayList<Object>(source);
        }
        if (source instanceof ArrayList) {
            // container.lines 声明为 List 但实际是 ArrayList；drawScreen 期间无人改它。
            @SuppressWarnings("unchecked")
            ArrayList<?> already = (ArrayList<?>) source;
            return already;
        }
        // 万一将来换成别的 List 实现，退回原版行为。
        return new ArrayList<Object>(source);
    }
}
