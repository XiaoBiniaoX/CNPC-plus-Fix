package bin.cnpcplus.client;

/**
 * 优化 #6（1.20.1 主内容移植）的版本号载体。
 *
 * <h2>为什么这个计数器必须放在普通类里，不能放在 Mixin 类里</h2>
 * 若写成 {@code MixinTextContainerInvalidate} 的 {@code @Unique private static int}
 * 字段再从 {@code MixinTextContainerLineData} 按名字调用读取——那是错的。
 *
 * <p>Mixin 的成员会被<b>拷贝进目标类</b>：{@code @Unique static int markupVersion}
 * 实际成为 {@code TextContainer} 的静态字段，{@code formatCodeText} 里的自增作用于
 * <b>那个拷贝</b>。而跨 mixin 按名字调用编译成
 * {@code INVOKESTATIC bin/cnpcplus/mixin/MixinTextContainerInvalidate.version()}——
 * 指向 mixin 类自己那份（classpath 上仍存在的真实类），初值 0 且永不自增。
 * 结果：版本号恒为 0、缓存永不失效——玩家改了代码却看到旧的高亮，且完全无报错。
 *
 * <p>普通类不参与成员拷贝，两个 mixin 通过它通信时读写同一份内存。
 *
 * <h2>为什么不用 volatile / AtomicInteger</h2>
 * TextContainer 与 GuiTextArea 全部在客户端渲染线程使用（Screen 生命周期回调），
 * 写与读同线程，无可见性问题；同步只会给每帧的读取加内存屏障，与优化目的相悖。
 */
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
