package top.cnpcplus.mixin;

import top.cnpcplus.perf.client.HighlightVersion;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.shared.client.gui.components.TextContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 优化 #6 的失效信号源：监视 {@code TextContainer.makeup} 的全部写入点。
 *
 * <h2>为什么需要它</h2>
 * {@link MixinTextContainerLineData} 缓存了 {@code getFormattedString()} 的结果。
 * 缓存必须在高亮标记表变化时失效，否则玩家改了代码却看到旧的着色。
 *
 * <h2>{@code makeup} 一共有几个写入点</h2>
 * 我把 {@code TextContainer} 逐行看过，写 {@code makeup} 的只有两处：
 * <ul>
 *   <li><b>{@code formatCodeText()}</b>（反编译源码 87-94 行）：
 *       {@code while ((markup = getNextMatching(start)) != null) { this.makeup.add(markup); start = markup.end; }}
 *       —— 由 {@code GuiTextArea.setText}（第 564-566 行）与
 *       {@code GuiTextArea.enableCodeHighlighting}（583-586 行）调用。
 *       这是玩家每敲一个键都会走的路径。</li>
 *   <li><b>{@code addMakeUp(int, int, char, int)}</b>（124-129 行）：
 *       先 {@code removeConflictingMarkUp}（内部 {@code makeup.removeAll(conflicting)}）
 *       再 {@code this.makeup.add(...)}。这是留给外部调用方的口子。</li>
 * </ul>
 *
 * <p>还有一个隐含情况：{@code new TextContainer(text)} 会造一个全新的
 * {@code makeup = new ArrayList<>()}。但那时 {@code LineData} 也全是新的，
 * 它们的缓存字段天然是 {@code null}，不需要额外的失效信号。
 *
 * <p>所以挂这两个方法就是完备的。
 *
 * <h2>版本号为什么放在 {@link HighlightVersion} 而不是本类的静态字段里</h2>
 * 这是一个 Mixin 的经典陷阱，详见 {@link HighlightVersion} 的类注释。
 * 简言之：mixin 类的 {@code @Unique static} 字段会被<b>拷贝进目标类</b>，
 * 而从另一个 mixin 类里按名字调用会指向<b>mixin 类自己那份</b>（永远不被自增），
 * 导致版本号恒为 0、缓存永不失效 —— 一个完全静默的错误。
 * 状态必须放在不参与拷贝的普通类里。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = TextContainer.class, remap = false)
public abstract class MixinTextContainerInvalidate {

    /**
     * {@code formatCodeText} 往 {@code makeup} 批量追加标记。
     *
     * <p>注入在 RETURN：等它写完再 bump，
     * 这样 bump 之后第一次 {@code getFormattedString} 拿到的一定是完整的新标记表。
     */
    @Inject(method = "formatCodeText", at = @At("RETURN"), remap = false)
    private void cnpcyouhua$bumpAfterFormat(CallbackInfo ci) {
        HighlightVersion.bump();
    }

    /**
     * {@code addMakeUp} 可能删除冲突标记并追加新标记。
     *
     * <p>它有一条早退路径（{@code removeConflictingMarkUp} 返回 false 时直接 return，
     * 不改 {@code makeup}）。那种情况下 bump 是多余的，
     * 但多 bump 一次的代价只是一次重算 —— 比漏 bump（显示错误着色）安全得多。
     */
    @Inject(method = "addMakeUp", at = @At("RETURN"), remap = false)
    private void cnpcyouhua$bumpAfterAdd(int start, int end, char c, int level, CallbackInfo ci) {
        HighlightVersion.bump();
    }
}
