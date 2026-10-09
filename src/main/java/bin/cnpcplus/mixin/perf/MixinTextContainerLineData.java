package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.client.HighlightVersion;
import bin.cnpcplus.config.CnpcPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 优化 #6 的核心（1.20.1 主内容移植）：{@code TextContainer$LineData.getFormattedString()} 的记忆化。
 *
 * <h2>原版做了什么（1.12.2 与 1.20.1 同构）</h2>
 * 对每一行：new StringBuilder(text)，然后<b>遍历整份文件的 makeup 标记表</b>，
 * 命中本行区间的标记逐个 {@code builder.insert(..., "\\uffff" + c)}。
 * 调用方是 {@code GuiTextArea.drawScreen} 的可见行循环——每帧、每个可见行一次。
 *
 * <h2>为什么「代码越长越卡」</h2>
 * 两层放大：每行都遍历全文标记表（500 行脚本轻松上千个标记 × 屏幕 30 行 =
 * 每帧数万次比较）；StringBuilder.insert 是 O(n) 的数组搬移。
 *
 * <h2>失效判据：全局单调版本号</h2>
 * {@code LineData} 是非静态内部类，访问 makeup 要经过编译器合成的 this$0——
 * @Shadow 它是脆的。改用 {@link MixinTextContainerInvalidate} 挂在 makeup 的
 * 全部写入点上 bump {@link HighlightVersion}。代价：任意容器的变化让所有行缓存
 * 一起失效——同一时刻只有一个 GuiTextArea 在编辑，makeup 只在玩家敲键时变化，可接受。
 *
 * <p><b>正确性</b>：版本号只增不减，缓存条目记录「算这个结果时的版本」，
 * 版本一变全部重算，不存在脏数据。
 *
 * <p>HEAD+RETURN 双注入：原方法体一字不改，其他附属对高亮算法的修改照常生效，
 * 我们缓存的是它们改过之后的结果。
 *
 * <p>目标是包级私有内部类（javap：{@code class TextContainer$LineData} 无 public），
 * 用 {@code targets} 字符串。注册在 mixins.json 的 client 段。
 *
 * <h2>配置</h2>
 * {@code textRenderOptimize}（默认 true）。关闭时 HEAD 不提前返回、RETURN 不存，
 * 每次都走原版全量计算。
 */
@Mixin(targets = "noppes.npcs.client.gui.util.TextContainer$LineData", remap = false)
public abstract class MixinTextContainerLineData {

    /** 上次算出的格式化串。 */
    @Unique
    private String cnpcplus$cached;

    /** 算出 {@link #cnpcplus$cached} 时的全局标记版本号。 */
    @Unique
    private int cnpcplus$cachedVersion = Integer.MIN_VALUE;

    @Inject(method = "getFormattedString", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$memoized(CallbackInfoReturnable<String> cir) {
        if (!CnpcPlusConfig.isTextRenderOptimizeEnabled()) {
            return;
        }
        if (this.cnpcplus$cached != null
                && this.cnpcplus$cachedVersion == HighlightVersion.current()) {
            cir.setReturnValue(this.cnpcplus$cached);
        }
    }

    @Inject(method = "getFormattedString", at = @At("RETURN"), remap = false)
    private void cnpcplus$remember(CallbackInfoReturnable<String> cir) {
        if (!CnpcPlusConfig.isTextRenderOptimizeEnabled()) {
            return;
        }
        this.cnpcplus$cached = cir.getReturnValue();
        this.cnpcplus$cachedVersion = HighlightVersion.current();
    }
}
