package top.cnpcplus.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.player.GuiQuestCompletion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.gui.TextScrollBar;

/**
 * 任务完成弹窗的只读正文原先会把所有折行直接画完，超出窗口就看不见。
 * 这里只限制绘制范围、滚轮和细滑条，不改编辑器、不改完成按钮、不改网络。
 */
@Mixin(value = GuiQuestCompletion.class, remap = false)
public abstract class MixinGuiQuestCompletionScroll extends noppes.npcs.client.gui.util.GuiNPCInterface {

    @Unique private static final int TEXT_TOP = 16;
    @Unique private static final int TEXT_BOTTOM_MARGIN = 30;
    @Unique private static final int LINE_HEIGHT = 9;
    @Unique private int cnpcplus$questRowStart;
    @Unique private int cnpcplus$questTotalRows;
    @Unique private int cnpcplus$questDrawIndex;

    @Unique
    private int cnpcplus$visibleRows() {
        return Math.max(1, (this.imageHeight - TEXT_TOP - TEXT_BOTTOM_MARGIN) / LINE_HEIGHT);
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void cnpcplus$resetQuestLineIndex(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.cnpcplus$questDrawIndex = 0;
    }

    @Redirect(
            method = "drawQuestText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280056_(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"),
            remap = false,
            require = 1
    )
    private int cnpcplus$drawClippedQuestLine(GuiGraphics graphics, Font font, String text,
                                            int x, int y, int color, boolean shadow) {
        int row = this.cnpcplus$questDrawIndex++;
        int visible = this.cnpcplus$visibleRows();
        if (row < this.cnpcplus$questRowStart || row >= this.cnpcplus$questRowStart + visible) {
            return 0;
        }
        int top = this.guiTop + TEXT_TOP;
        int bottom = this.guiTop + this.imageHeight - TEXT_BOTTOM_MARGIN;
        graphics.enableScissor(this.guiLeft + 4, top, this.guiLeft + 170, bottom);
        try {
            return graphics.drawString(font, text, x, y - this.cnpcplus$questRowStart * LINE_HEIGHT, color, shadow);
        } finally {
            graphics.disableScissor();
        }
    }

    @Inject(
            method = "m_88315_",
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/GuiQuestCompletion;drawQuestText(Lnet/minecraft/client/gui/GuiGraphics;)V", shift = At.Shift.AFTER),
            remap = false,
            require = 1
    )
    private void cnpcplus$drawQuestScrollbar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.cnpcplus$questTotalRows = this.cnpcplus$questDrawIndex;
        this.cnpcplus$questRowStart = TextScrollBar.clamp(
                this.cnpcplus$questRowStart, this.cnpcplus$questTotalRows, this.cnpcplus$visibleRows());
        int top = this.guiTop + TEXT_TOP;
        int bottom = this.guiTop + this.imageHeight - TEXT_BOTTOM_MARGIN;
        TextScrollBar.draw(graphics, this.guiLeft + 168, top, bottom,
                this.cnpcplus$questTotalRows, this.cnpcplus$visibleRows(), this.cnpcplus$questRowStart);
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        int top = this.guiTop + TEXT_TOP;
        int bottom = this.guiTop + this.imageHeight - TEXT_BOTTOM_MARGIN;
        if (button == 0 && this.cnpcplus$questTotalRows > this.cnpcplus$visibleRows()
                && mouseX >= this.guiLeft + 166 && mouseX <= this.guiLeft + 172 && mouseY >= top && mouseY <= bottom) {
            this.cnpcplus$questRowStart = TextScrollBar.rowForMouse((int) mouseY, top, bottom,
                    this.cnpcplus$questTotalRows, this.cnpcplus$visibleRows());
            return true;
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        if (super.m_6050_(mouseX, mouseY, delta)) return true;
        if (delta == 0.0D) return false;
        int top = this.guiTop + TEXT_TOP;
        int bottom = this.guiTop + this.imageHeight - TEXT_BOTTOM_MARGIN;
        if (mouseX < this.guiLeft + 4 || mouseX > this.guiLeft + 170 || mouseY < top || mouseY > bottom) {
            return false;
        }
        int visible = this.cnpcplus$visibleRows();
        if (this.cnpcplus$questTotalRows <= visible) return false;
        this.cnpcplus$questRowStart = TextScrollBar.clamp(
                this.cnpcplus$questRowStart + (delta > 0.0D ? -3 : 3),
                this.cnpcplus$questTotalRows, visible);
        return true;
    }
}
