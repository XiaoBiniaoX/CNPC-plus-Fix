package bin.cnpcplus.mixin;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.gui.TextScrollBar;
import bin.cnpcplus.util.FormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.shared.client.util.NoppesStringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiDialogInteract.class)
public class GuiDialogInteractMixin {

    @Shadow(remap = false)
    private int rowStart;

    @Shadow(remap = false)
    private int rowTotal;

    @Shadow(remap = false)
    private int dialogHeight;

    /** 行高与下面实际绘制所用的字体保持同一套，否则裁剪窗口和行距会错位。 */
    @Unique
    private int cnpcplus$lineHeight() {
        return Math.max(1, Minecraft.getInstance().font.lineHeight);
    }

    @Unique
    private int cnpcplus$visibleRows() {
        return Math.max(1, this.dialogHeight / this.cnpcplus$lineHeight());
    }

    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$drawDialogText(GuiGraphics graphics, String text, int left, int color, int count, CallbackInfo ci) {
        GuiDialogInteract self = (GuiDialogInteract)(Object)this;
        String fmt = CnpcPlusConfig.DIALOG_TEXT_FORMAT.get();
        int hex = FormatUtil.parseHexColor(CnpcPlusConfig.DIALOG_TEXT_COLOR.get(), 0xE0E0E0);
        String drawn = FormatUtil.applyDefault(text, fmt);
        int drawColor = FormatUtil.resolveColor(hex, fmt, color);
        if (FormatUtil.hasFormatCodes(text)) {
            drawColor = 0xFFFFFF;
        }

        int lineHeight = this.cnpcplus$lineHeight();
        int height = count - this.rowStart;
        // 超出对话框上下边界的行直接不画，原实现会一路画到屏幕外。
        if (height < 0 || height * lineHeight >= this.dialogHeight) {
            ci.cancel();
            return;
        }

        int x = self.guiLeft + left;
        int y = self.guiTop + height * lineHeight;
        int clipLeft = self.guiLeft - 60;
        int clipRight = self.guiLeft + self.imageWidth + 120;
        graphics.enableScissor(clipLeft, self.guiTop, clipRight, self.guiTop + this.dialogHeight);
        try {
            graphics.drawString(Minecraft.getInstance().font, drawn, x, y, drawColor, false);
        } finally {
            graphics.disableScissor();
        }
        ci.cancel();
    }

    /** 复用原生 rowStart/rowTotal 画细滑条，不改变追加新对话后自动滚到底的语义。 */
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void cnpcplus$drawDialogScrollbar(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiDialogInteract self = (GuiDialogInteract)(Object)this;
        TextScrollBar.draw(graphics, self.guiLeft + self.imageWidth + 116, self.guiTop,
                self.guiTop + this.dialogHeight, this.rowTotal, this.cnpcplus$visibleRows(), this.rowStart);
    }

    /**
     * GuiDialogInteract 自己声明了 mouseClicked 且总是 return true，
     * 所以只能在 HEAD 拦截滑条点击，命中才提前返回，其余放行原有选项逻辑。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$scrollbarClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        GuiDialogInteract self = (GuiDialogInteract)(Object)this;
        int top = self.guiTop;
        int bottom = self.guiTop + this.dialogHeight;
        int barX = self.guiLeft + self.imageWidth + 116;
        if (button == 0 && this.rowTotal > this.cnpcplus$visibleRows()
                && mouseX >= barX - 2 && mouseX <= barX + 4 && mouseY >= top && mouseY <= bottom) {
            this.rowStart = TextScrollBar.rowForMouse((int) mouseY, top, bottom,
                    this.rowTotal, this.cnpcplus$visibleRows());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$scrollDialogText(double mouseX, double mouseY, double scrollX, double scrollY,
                                           CallbackInfoReturnable<Boolean> cir) {
        GuiDialogInteract self = (GuiDialogInteract)(Object)this;
        // 1.21.1 竖直增量在第四个参数；横向滚轮设备只给 scrollX 时退回它。
        double delta = scrollY != 0.0 ? scrollY : scrollX;
        if (delta == 0.0) return;
        if (mouseX < self.guiLeft - 60 || mouseX > self.guiLeft + self.imageWidth + 120
                || mouseY < self.guiTop || mouseY > self.guiTop + this.dialogHeight) {
            return;
        }
        int visible = this.cnpcplus$visibleRows();
        if (this.rowTotal <= visible) return;
        this.rowStart = TextScrollBar.clamp(this.rowStart + (delta > 0.0 ? -3 : 3), this.rowTotal, visible);
        cir.setReturnValue(true);
    }

    @Redirect(method = "drawLinedOptions", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/util/NoppesStringUtils;formatText(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"), remap = false)
    private String cnpcplus$optionFormat(String text, Object[] obs) {
        String fmt = CnpcPlusConfig.DIALOG_OPTION_FORMAT.get();
        String prepared = FormatUtil.applyDefault(text, fmt);
        return NoppesStringUtils.formatText(prepared.replace('\u00a7', '&'), obs);
    }

    @ModifyArg(method = "drawLinedOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"), index = 4, remap = false)
    private int cnpcplus$optionColor(int color) {
        return color;
    }

    @Redirect(method = "drawWheel", at = @At(value = "FIELD", target = "Lnoppes/npcs/controllers/data/DialogOption;optionColor:I"), remap = false)
    private int cnpcplus$wheelOptionColor(DialogOption option) {
        return option.optionColor;
    }
}
