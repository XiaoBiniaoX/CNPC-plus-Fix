package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.gui.player.moderngui.GuiDialogModern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.gui.TextScrollBar;

/**
 * 现代对话框原先按全文高度扩张底部面板，长文本会顶出自己的 UI。
 * 布局只使用固定可见行数，正文在该区域内滚动；选项位置与既有缩放 mixin 无关。
 */
@Mixin(value = GuiDialogModern.class, remap = false)
public abstract class MixinGuiDialogModernScroll extends noppes.npcs.client.gui.util.GuiNPCInterface {

    @Unique private static final int VISIBLE_ROWS = 10;
    @Unique private int cnpcplus$modernTotalRows;
    @Unique private int cnpcplus$modernRowStart;
    @Unique private int cnpcplus$clipLeft;
    @Unique private int cnpcplus$clipTop;
    @Unique private int cnpcplus$clipRight;
    @Unique private int cnpcplus$clipBottom;

    @Redirect(
            method = "m_88315_",
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/moderngui/GuiDialogModern;getLineCount(Ljava/lang/String;I)I"),
            remap = false,
            require = 1
    )
    private int cnpcplus$capModernLayoutRows(GuiDialogModern self, String text, int width) {
        int total = self.getLineCount(text, width);
        this.cnpcplus$modernTotalRows = total;
        this.cnpcplus$modernRowStart = TextScrollBar.clamp(this.cnpcplus$modernRowStart, total, VISIBLE_ROWS);
        return Math.min(total, VISIBLE_ROWS);
    }

    @Redirect(
            method = "m_88315_",
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/moderngui/GuiDialogModern;drawTextBlock(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;III)V"),
            remap = false,
            require = 1
    )
    private void cnpcplus$drawScrollableModernText(GuiDialogModern self, GuiGraphics graphics,
                                                  String text, int x, int y, int width) {
        int fontHeight = ClientProxy.Font.height(null);
        if (fontHeight <= 0) return;
        int rows = Math.min(this.cnpcplus$modernTotalRows, VISIBLE_ROWS);
        this.cnpcplus$clipLeft = x;
        this.cnpcplus$clipTop = y;
        this.cnpcplus$clipRight = x + width;
        this.cnpcplus$clipBottom = y + rows * fontHeight;
        graphics.enableScissor(this.cnpcplus$clipLeft, this.cnpcplus$clipTop, this.cnpcplus$clipRight, this.cnpcplus$clipBottom);
        try {
            self.drawTextBlock(graphics, text, x, y - this.cnpcplus$modernRowStart * fontHeight, width);
        } finally {
            graphics.disableScissor();
        }
        TextScrollBar.draw(graphics, this.cnpcplus$clipRight - 2, this.cnpcplus$clipTop, this.cnpcplus$clipBottom,
                this.cnpcplus$modernTotalRows, VISIBLE_ROWS, this.cnpcplus$modernRowStart);
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        if (button == 0 && this.cnpcplus$modernTotalRows > VISIBLE_ROWS
                && mouseX >= this.cnpcplus$clipRight - 4 && mouseX <= this.cnpcplus$clipRight + 2
                && mouseY >= this.cnpcplus$clipTop && mouseY <= this.cnpcplus$clipBottom) {
            this.cnpcplus$modernRowStart = TextScrollBar.rowForMouse((int) mouseY,
                    this.cnpcplus$clipTop, this.cnpcplus$clipBottom, this.cnpcplus$modernTotalRows, VISIBLE_ROWS);
            return true;
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        if (super.m_6050_(mouseX, mouseY, delta)) return true;
        if (delta == 0.0D || this.cnpcplus$modernTotalRows <= VISIBLE_ROWS) return false;
        if (mouseX < this.cnpcplus$clipLeft || mouseX > this.cnpcplus$clipRight
                || mouseY < this.cnpcplus$clipTop || mouseY > this.cnpcplus$clipBottom) {
            return false;
        }
        this.cnpcplus$modernRowStart = TextScrollBar.clamp(
                this.cnpcplus$modernRowStart + (delta > 0.0D ? -3 : 3),
                this.cnpcplus$modernTotalRows, VISIBLE_ROWS);
        return true;
    }
}
