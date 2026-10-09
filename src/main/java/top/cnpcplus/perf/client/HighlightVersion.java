package top.cnpcplus.perf.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 优化 #6 的版本号载体。
 *
 * <h2>为什么这个计数器必须放在普通类里，不能放在 Mixin 类里</h2>
 * 最初我把它写成 {@code MixinTextContainerInvalidate} 的一个
 * {@code @Unique private static int} 字段，然后从
 * {@code MixinTextContainerLineData} 调它的静态方法读取。<b>那是错的。</b>
 *
 * <p>原因在于 Mixin 的工作方式：mixin 类的成员会被<b>拷贝进目标类</b>。
 * 所以 {@code @Unique static int markupVersion} 实际会成为
 * {@code TextContainer} 的一个静态字段，{@code formatCodeText} 里的
 * {@code ++} 操作也会被改写成对<b>那个拷贝</b>的自增。
 *
 * <p>而 {@code MixinTextContainerLineData} 的方法体里写
 * {@code MixinTextContainerInvalidate.version()} 会编译成
 * <pre>INVOKESTATIC bin/cnpcyouhua/mixin/MixinTextContainerInvalidate.version()</pre>
 * —— 指向的是<b>mixin 类自己</b>，而不是被拷贝到 {@code TextContainer} 里的那份。
 * mixin 类在运行期仍然是 classpath 上的一个真实类，它的静态字段初值是 0 且<b>永远不会被自增</b>。
 *
 * <p>结果就是：版本号读到的永远是 0，而缓存里记的也是 0 —— <b>缓存永不失效</b>。
 * 玩家改了代码却看到旧的高亮着色，而且完全没有报错，是个纯静默的错误。
 *
 * <p>解法很简单：把状态放在一个<b>普通类</b>里。
 * 普通类不参与 mixin 的成员拷贝，两个 mixin 通过它通信时读写的是同一份内存。
 *
 * <h2>为什么不用 volatile / AtomicInteger</h2>
 * {@code TextContainer} 与 {@code GuiTextArea} 全部在客户端渲染线程上使用
 * （{@code render} / {@code keyPressed} / {@code charTyped} 都是 Screen 的生命周期回调）。
 * 写与读在同一线程，不存在可见性问题。
 * 加同步只会给每帧的版本号读取加一道内存屏障，与本项优化的目的相悖。
 */
@OnlyIn(Dist.CLIENT)
public final class HighlightVersion {

    /** 高亮标记表的全局版本号。只增不减。 */
    private static int version;

    private HighlightVersion() {
    }

    /** 当前版本。{@code MixinTextContainerLineData} 用它判断缓存是否过期。 */
    public static int current() {
        return version;
    }

    /** 标记表变了。{@code MixinTextContainerInvalidate} 在写入点调用。 */
    public static void bump() {
        version++;
    }
}
