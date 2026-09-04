package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.gui.TextScrollBar;

/**
 * 普通对话框已有 rowStart，但只用于自动定位到末尾，玩家无法回看被顶出的旧文本。
 * 这里复用原字段做滚轮/细滑条，不改变追加新对话后自动滚到底的语义。
 */
@Mixin(value = GuiDialogInteract.class, remap = false)
public abstract class MixinGuiDialogInteractScroll extends noppes.npcs.client.gui.util.GuiNPCInterface {

    @Shadow(remap = false) private int rowStart;
    @Shadow(remap = false) private int rowTotal;
    @Shadow(remap = false) private int dialogHeight;

    @Unique
    private int cnpcplus$visibleRows() {
        int height = ClientProxy.Font.height(null);
        if (height <= 0) return 1;
        return Math.max(1, this.dialogHeight / height);
    }

    @Invoker("drawString")
    protected abstract void cnpcplus$invokeDrawString(GuiGraphics graphics, String text, int left, int color, int count);

    @Redirect(
            method = "m_88315_",
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/GuiDialogInteract;drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;III)V"),
            remap = false
    )
    private void cnpcplus$drawClippedDialogLine(GuiDialogInteract self, GuiGraphics graphics,
                                               String text, int left, int color, int count) {
        int fontHeight = ClientProxy.Font.height(null);
        if (fontHeight <= 0) return;
        int visibleRow = count - this.rowStart;
        if (visibleRow < 0 || visibleRow * fontHeight >= this.dialogHeight) return;
        int clipLeft = this.guiLeft - 60;
        int clipRight = this.guiLeft + this.imageWidth + 120;
        int clipTop = this.guiTop;
        int clipBottom = this.guiTop + this.dialogHeight;
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        try {
            this.cnpcplus$invokeDrawString(graphics, text, left, color, count);
        } finally {
            graphics.disableScissor();
        }
    }

    @Inject(method = "m_88315_", at = @At("TAIL"), remap = false)
    private void cnpcplus$drawDialogScrollbar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int visible = this.cnpcplus$visibleRows();
        TextScrollBar.draw(graphics, this.guiLeft + this.imageWidth + 116, this.guiTop,
                this.guiTop + this.dialogHeight, this.rowTotal, visible, this.rowStart);
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        int top = this.guiTop;
        int bottom = this.guiTop + this.dialogHeight;
        int barX = this.guiLeft + this.imageWidth + 116;
        if (button == 0 && this.rowTotal > this.cnpcplus$visibleRows()
                && mouseX >= barX - 2 && mouseX <= barX + 4 && mouseY >= top && mouseY <= bottom) {
            this.rowStart = TextScrollBar.rowForMouse((int) mouseY, top, bottom, this.rowTotal, this.cnpcplus$visibleRows());
            return true;
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        if (super.m_6050_(mouseX, mouseY, delta)) return true;
        if (delta == 0.0D) return false;
        if (mouseX < this.guiLeft - 60 || mouseX > this.guiLeft + this.imageWidth + 120
                || mouseY < this.guiTop || mouseY > this.guiTop + this.dialogHeight) {
            return false;
        }
        int visible = this.cnpcplus$visibleRows();
        if (this.rowTotal <= visible) return false;
        this.rowStart = TextScrollBar.clamp(this.rowStart + (delta > 0.0D ? -3 : 3), this.rowTotal, visible);
        return true;
    }
}
