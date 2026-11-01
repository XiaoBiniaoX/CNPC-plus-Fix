package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.client.gui.player.GuiQuestCompletion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.gui.TextScrollBar;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.StyledTextRender;

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
    @Shadow private IQuest quest;
    @Unique private StyledText cnpcplus$lastText;
    @Unique private List<StyledText.Line> cnpcplus$questLines;

    @Unique
    private int cnpcplus$visibleRows() {
        return Math.max(1, (this.imageHeight - TEXT_TOP - TEXT_BOTTOM_MARGIN) / LINE_HEIGHT);
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$pages(CallbackInfo ci) {
        addButton(new GuiButtonNop(this, 480, guiLeft + 4, guiTop + imageHeight - 24, 26, 20, "<"));
        addButton(new GuiButtonNop(this, 481, guiLeft + 146, guiTop + imageHeight - 24, 26, 20, ">"));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$page(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 480 && button.id != 481) return;
        cnpcplus$questRowStart = TextScrollBar.clamp(cnpcplus$questRowStart + (button.id == 480 ? -1 : 1) * cnpcplus$visibleRows(),
                cnpcplus$questTotalRows, cnpcplus$visibleRows());
        ci.cancel();
    }

    @Inject(method = "drawQuestText", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$drawStyledQuest(GuiGraphics graphics, CallbackInfo ci) {
        StyledText text = StyledTextRender.format(TextStyles.get(quest, TextStyles.COMPLETE, quest.getCompleteText()), player);
        if (!text.equals(cnpcplus$lastText)) {
            cnpcplus$lastText = text;
            cnpcplus$questLines = StyledTextRender.lines(text, 162, true);
        }
        cnpcplus$questTotalRows = cnpcplus$questLines.size();
        int visible = this.cnpcplus$visibleRows();
        cnpcplus$questRowStart = TextScrollBar.clamp(cnpcplus$questRowStart, cnpcplus$questTotalRows, visible);
        int top = this.guiTop + TEXT_TOP;
        int bottom = this.guiTop + this.imageHeight - TEXT_BOTTOM_MARGIN;
        graphics.enableScissor(this.guiLeft + 4, top, this.guiLeft + 166, bottom);
        try {
            for (int row = 0; row < visible && row + cnpcplus$questRowStart < cnpcplus$questTotalRows; row++) {
                StyledTextRender.draw(graphics, cnpcplus$questLines.get(row + cnpcplus$questRowStart).component(),
                        guiLeft + 4, top + row * LINE_HEIGHT, CustomNpcResourceListener.getDefaultTextColor(), true);
            }
        } finally {
            graphics.disableScissor();
        }
        if (getButton(480) != null) getButton(480).visible = cnpcplus$questRowStart > 0;
        if (getButton(481) != null) getButton(481).visible = cnpcplus$questRowStart + visible < cnpcplus$questTotalRows;
        ci.cancel();
    }

    @Inject(
            method = "m_88315_",
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/GuiQuestCompletion;drawQuestText(Lnet/minecraft/client/gui/GuiGraphics;)V", shift = At.Shift.AFTER),
            remap = false,
            require = 1
    )
    private void cnpcplus$drawQuestScrollbar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
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
