package bin.cnpcplus.mixin.gui;

import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 1.21.1 所有 CNPC 滚动列表（含模型/替身实体选择界面）滚轮只往下走的问题。
 *
 * <p>1.20.1 的 {@code mouseScrolled} 只有三个参数，第三个就是竖直增量。
 * 1.21.1 原版签名是 {@code (mouseX, mouseY, scrollX, scrollY)}，竖直增量在第四个参数；
 * CNPC 移植时仅追加了未使用的 {@code arg4}，仍拿第三个参数判方向。
 * 普通鼠标滚轮的横向增量恒为 0，于是 {@code 0.0 > 0.0} 恒假，每次滚动都执行 +14 往下走。
 *
 * <p>这里改用第四个参数判方向，并补回 1.20.1 存在、1.21.1 被删掉的零增量守卫。
 */
@Mixin(value = GuiCustomScrollNop.class, remap = false)
public class MixinGuiCustomScrollNopScrollDirection {

    @Shadow
    private int scrollY;

    @Shadow
    private int maxScrollY;

    @Shadow
    private boolean mouseInList;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$useVerticalScroll(double mouseX, double mouseY, double scrollX, double scrollY,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!this.mouseInList) {
            cir.setReturnValue(false);
            return;
        }
        // 优先取竖直增量；横向滚轮设备只给 scrollX 时退回它，避免这类设备完全无法滚动。
        double delta = scrollY != 0.0 ? scrollY : scrollX;
        if (delta == 0.0) {
            cir.setReturnValue(false);
            return;
        }
        // 步长 14 与原实现一致（等于一行高度），仅方向判断来源改正。
        this.scrollY += delta > 0.0 ? -14 : 14;
        if (this.scrollY > this.maxScrollY) {
            this.scrollY = this.maxScrollY;
        }
        if (this.scrollY < 0) {
            this.scrollY = 0;
        }
        cir.setReturnValue(true);
    }
}
