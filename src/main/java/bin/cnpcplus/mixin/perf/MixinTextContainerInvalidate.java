package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.client.HighlightVersion;
import noppes.npcs.client.gui.util.TextContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 优化 #6 的失效信号源（1.20.1 主内容移植）：监视 {@code TextContainer.makeup} 的全部写入点。
 *
 * <h2>为什么需要它</h2>
 * {@link MixinTextContainerLineData} 缓存了 {@code getFormattedString()} 的结果。
 * 缓存必须在高亮标记表变化时失效，否则玩家改了代码却看到旧的着色。
 *
 * <h2>{@code makeup} 一共有几个写入点（javap 实证，1.12.2 05Jul20）</h2>
 * 全类检索 {@code Field makeup} 的访问点：
 * <ul>
 *   <li>{@code TextContainer(String)}：putfield 创建空表——那时 LineData 全是新的，
 *       缓存字段天然为 null，不需要失效信号；</li>
 *   <li>{@code formatCodeText()}：getfield 后 List.add 批量追加标记
 *       ——由 setText/enableCodeHighlighting 调用，玩家每敲一个键都走；</li>
 *   <li>{@code addMakeUp(int,int,char,int)}：getfield 后追加（内部还可能 removeAll 冲突标记）；</li>
 *   <li>{@code removeConflictingMarkUp}：只被 addMakeUp 调用，已覆盖；</li>
 *   <li>{@code getFormattedString}：只读。</li>
 * </ul>
 * 所以挂 formatCodeText 与 addMakeUp 两个方法的 RETURN 就是完备的。
 *
 * <p>版本号载体见 {@link HighlightVersion}（不能放 mixin 类的 @Unique static 里）。
 * 失效信号无条件 bump：关闭 {@code textRenderOptimize} 时缓存不读，bump 是无害空操作。
 */
@Mixin(value = TextContainer.class, remap = false)
public abstract class MixinTextContainerInvalidate {

    /**
     * {@code formatCodeText} 往 {@code makeup} 批量追加标记。
     * 注入在 RETURN：等它写完再 bump，bump 后第一次 getFormattedString 拿到的一定是完整新表。
     */
    @Inject(method = "formatCodeText", at = @At("RETURN"), remap = false)
    private void cnpcplus$bumpAfterFormat(CallbackInfo ci) {
        HighlightVersion.bump();
    }

    /**
     * {@code addMakeUp} 可能删除冲突标记并追加新标记。
     * 它有早退路径（不改 makeup 时直接 return）；多 bump 的代价只是一次重算——
     * 比漏 bump（显示错误着色）安全得多。
     */
    @Inject(method = "addMakeUp", at = @At("RETURN"), remap = false)
    private void cnpcplus$bumpAfterAdd(int start, int end, char c, int level, CallbackInfo ci) {
        HighlightVersion.bump();
    }
}
