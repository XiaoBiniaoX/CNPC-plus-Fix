package top.cnpcplus.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

/**
 * 优化 #11：名牌标题每帧重复构造 {@code Component}。
 *
 * <h2>原版做了什么</h2>
 * {@code RenderNPCInterface.renderLivingLabel}（反编译源码 101-131 行）第 118-126 行：
 * <pre>
 * if (!npc.display.getTitle().isEmpty()) {
 *     MutableComponent title = Component.literal("&lt;")
 *             .append(Component.translatable(npc.display.getTitle().replace('&amp;', '§')))
 *             .append("&gt;");
 *     float f3 = 0.6f;
 *     matrixStack.translate(0, 4, 0);
 *     matrixStack.scale(f3, f3, f3);
 *     fontrenderer.drawInBatch(title, -fontrenderer.width(title) / 2, 0, color, false,
 *                              matrix4f, buffer, Font.DisplayMode.NORMAL, j, light);
 *     matrixStack.scale(1/f3, 1/f3, 1/f3);
 *     y = -10.0f;
 * }
 * </pre>
 *
 * <p>每帧、每个显示名牌且玩家在 8 格内的 NPC，这一段产生：
 * <ul>
 *   <li>{@code Component.translatable(...)} —— 一个 {@code TranslatableContents} 加一个 {@code MutableComponent}</li>
 *   <li>{@code Component.literal("&lt;")} —— 又一个 {@code MutableComponent}</li>
 *   <li>两次 {@code append} —— 各自 lazily 建一个 siblings {@code ArrayList}</li>
 *   <li>{@code String.replace(char, char)} —— 标题里有 {@code &amp;} 时是一个新 {@code String}</li>
 * </ul>
 * 而 {@code display.getTitle()} 的变化频率是「管理员改一次配置」级别。
 *
 * <h2>本类的红线：只换一条指令，不碰矩阵与分支</h2>
 * 名牌渲染有三个必须完好保留的行为：
 * <ol>
 *   <li><b>可以旋转</b> —— 第 108 行
 *       {@code matrixStack.mulPose(entityRenderDispatcher.cameraOrientation())}
 *       让名牌朝向相机；第 113 行 {@code scale(-f2, -f2, f2)} 的负号处理翻转。</li>
 *   <li><b>有开关渲染</b> —— {@code renderNameTag} 第 95 行的 {@code display.showName()}
 *       决定是否进本方法；第 118 行的 {@code getTitle().isEmpty()} 决定是否画标题；
 *       第 116-117 行的 {@code isInRange(camera, 8.0)} 决定 8 格外不画文字。</li>
 *   <li><b>被方块遮挡一半</b> —— 由第 123/128 行传给 {@code drawInBatch} 的
 *       {@code Font.DisplayMode.NORMAL} 实现。它用带深度测试的渲染类型，
 *       所以名牌会被方块正确遮挡（对比 {@code SEE_THROUGH} 是穿墙可见）。</li>
 * </ol>
 *
 * <p>另外还有一个<b>极易踩坑的原版怪癖</b>：
 * 第 114 行 {@code Matrix4f matrix4f = matrixStack.last().pose()} 先捕获了矩阵，
 * 而第 121-122 行的 {@code translate(0, 4, 0)} 与 {@code scale(0.6f)}
 * <b>发生在捕获之后</b>。{@code drawInBatch} 用的是捕获的 {@code matrix4f}
 * 而非当前位姿，所以那两个变换<b>对实际绘制没有任何影响</b>
 * （大概是从旧版 {@code GlStateManager} 时代遗留的）。
 *
 * <p>如果重写整个方法而没有原样保留这个怪癖，标题位置就会偏移 ——
 * 这正是「把它炸了」的典型方式。
 *
 * <p>所以本类<b>只用一个 {@code @Redirect} 换掉一条 {@code INVOKESTATIC} 指令</b>。
 * 矩阵操作、分支判断、{@code DisplayMode}、颜色、光照、宽度计算 —— 一条都不动，
 * 仍然是 CNPC 原来的字节码在执行，包括那个矩阵捕获怪癖。
 *
 * <h2>为什么只拦 {@code translatable} 而不顺手拦 {@code String.replace}</h2>
 * 一开始我打算把 {@code replace} 也拦掉（缓存命中时连替换都省）。
 * 但那样两个 redirect 之间就有了<b>隐式依赖</b>：
 * {@code replace} 透传原串、{@code translatable} 负责补做替换。
 * 如果其中一个因为字节码变化而匹配失败（两者都带 {@code require = 0}），
 * 另一个会拿到错误的输入 —— {@code translatable} 失配时标题的 {@code &amp;}
 * 就永远不会变成 {@code §}，颜色代码全废。
 *
 * <p>一条 redirect 只有「生效」和「不生效」两种状态，两条就有四种，
 * 其中两种是错的。为了省一次 {@code String.replace}（无匹配时 JDK 直接返回 {@code this}，
 * 有匹配时也就一次字符数组拷贝）不值得引入这个风险。
 *
 * <p>所以现在的键是<b>已经替换过</b>的标题串。判据依然直接：
 * 标题不改 → 替换结果不变 → 缓存命中。
 *
 * <h2>缓存的正确性</h2>
 * 缓存的 {@code MutableComponent} 只被用在两处：
 * 作为 {@code append} 的参数（{@code append} 修改的是<b>父</b>组件的 siblings，
 * 不动参数本身），以及间接进入 {@code Font.width} / {@code drawInBatch}
 * （两者都只读）。所以跨帧、跨 NPC 共享同一个实例是安全的。
 *
 * <p>用<b>静态</b>池而不是实例字段：{@code RenderNPCInterface} 是按实体类型单例的，
 * 屏幕上不同标题的 NPC 会交替渲染。实例字段等价于容量 1 的缓存，
 * 交替时会退化成每帧重建。静态池让一整队「卫兵」共享同一个 {@code Component}。
 *
 * <h2>为什么不缓存 {@code Font.width} 的结果</h2>
 * {@code Font.width(FormattedText)} 每帧对标题和名字各调一次，
 * 内部走 {@code StringSplitter} 做完整文本布局 —— 说实话比构造 {@code Component} 更贵。
 *
 * <p>但它的另一个入参是 {@code npc.getName()}，那是 vanilla {@code Entity.getName()}
 * 的返回值，可能因队伍前缀/自定义名而变，需要额外失效判据。
 * 而 {@code @Redirect} 到 {@code Font.width} 会同时命中标题和名字两处调用，难以区分。
 * 这一档留作后续 —— 当前这一改已经消掉了这条路径上的对象分配。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class MixinRenderNPCInterfaceLabel {

    /** 上限。同时在场的不同标题不会多，超过就退回每帧构造（行为不变，只是不省）。 */
    @Unique private static final int MAX_TITLES = 256;

    /**
     * {@code 已替换的标题串 -> 构造好的可翻译 Component}。
     *
     * <p>全部访问都在客户端渲染线程（{@code renderLivingLabel} 只由
     * {@code renderNameTag} 调用，后者在实体渲染流程里），不需要同步。
     */
    @Unique private static final Map<String, MutableComponent> TITLE_POOL = new HashMap<>(32);

    /**
     * 替换第 119 行的 {@code Component.translatable(String)}。
     *
     * <p><b>为什么写 SRG 名 {@code m_237115_} 而不是 {@code translatable}</b>：
     * 本 mixin 是 {@code remap = false}（因为注入目标 {@code renderLivingLabel}
     * 是 CNPC 自有方法，不能让 Mixin 去 refmap 里查它）。
     * 而 {@code remap = false} 是<b>整个注解树</b>生效的 ——
     * 里面的 {@code @At.target} 同样不会被重映射。
     * 所以这里必须直接写生产环境类文件里的真实名字，也就是 SRG 名。
     *
     * <p>{@code require = 0}：这是纯分配优化，不涉及正确性。
     * 万一 CNPC 改了标题的构造写法导致匹配不上，静默失效（退回原版每帧构造）
     * 远好于让整个客户端启动失败。
     */
    @Redirect(
            method = "renderLivingLabel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;m_237115_(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
            require = 0,
            remap = false
    )
    private MutableComponent cnpcyouhua$cachedTitleComponent(String translationKey) {
        MutableComponent cached = TITLE_POOL.get(translationKey);
        if (cached != null) {
            return cached;
        }
        MutableComponent built = Component.translatable(translationKey);
        if (TITLE_POOL.size() < MAX_TITLES) {
            TITLE_POOL.put(translationKey, built);
        }
        return built;
    }
}
