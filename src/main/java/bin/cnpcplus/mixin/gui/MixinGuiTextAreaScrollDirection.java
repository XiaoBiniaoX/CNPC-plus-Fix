package bin.cnpcplus.mixin.gui;

import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.TextContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复脚本编辑页等文本区滚轮完全无效的问题（1.21.1 独有）。
 *
 * <p>1.20.1 的 {@code mouseScrolled} 只有三个参数，第三个就是竖直增量。
 * 1.21.1 原版签名为 {@code (mouseX, mouseY, scrollX, scrollY)}，竖直增量在第四个参数。
 * CNPC 移植时只追加了未使用的 {@code arg4}，仍读第三个参数，且开头有
 * {@code if (scrolled == 0.0) return true;}。普通鼠标滚轮的横向增量恒为 0，
 * 于是每次滚动都在第一行直接返回，滚轮彻底失效，只能手动拖滑块。
 *
 * <p>与列表控件那处（滚轮只往下走）是同一版本迁移遗漏的两种表现：
 * 列表没有零增量守卫，故恒定向下；文本区有守卫，故完全不动。
 */
@Mixin(value = GuiTextArea.class, remap = false)
public class MixinGuiTextAreaScrollDirection {

    @Shadow
    private int scrolledLine;

    @Shadow
    private TextContainer container;

    @Shadow
    public int height;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$useVerticalScroll(double mouseX, double mouseY, double scrollX, double scrollY,
                                            CallbackInfoReturnable<Boolean> cir) {
        // 优先取竖直增量；横向滚轮设备只给 scrollX 时退回它，避免这类设备完全无法滚动。
        double delta = scrollY != 0.0 ? scrollY : scrollX;
        // 与原版一致返回 true：文本区激活时吞掉滚轮事件，不再往下层控件传递。
        if (delta == 0.0 || this.container == null || this.container.lineHeight <= 0) {
            cir.setReturnValue(true);
            return;
        }
        // 步长保持原实现的一行，仅方向判断来源改正。
        this.scrolledLine += delta > 0.0 ? -1 : 1;
        this.scrolledLine = Math.max(
                Math.min(this.scrolledLine, this.container.linesCount - this.height / this.container.lineHeight), 0);
        cir.setReturnValue(true);
    }
}
