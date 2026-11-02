package bin.cnpcplus.mixin.quest;

import bin.cnpcplus.gui.TextScrollBar;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.api.handler.data.IQuest;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.client.gui.player.GuiQuestCompletion;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.StyledTextRender;

/**
 * 任务完成文本原先把 TextBlockClient 折出的所有行无条件画完，超出窗口就画到屏幕外。
 * 这里只加裁剪、行过滤、滚轮和细滑条，不改任务数据、不改完成按钮、不动网络。
 */
@Mixin(value = GuiQuestCompletion.class, remap = false)
public abstract class MixinGuiQuestCompletionScroll extends GuiNPCInterface {

    @Unique
    private static final int CNPCPLUS_TEXT_TOP = 16;
    @Unique
    private static final int CNPCPLUS_TEXT_BOTTOM_MARGIN = 30;
    @Unique
    private static final int CNPCPLUS_LINE_HEIGHT = 9;

    @Unique
    private int cnpcplus$questRowStart;
    @Unique
    private int cnpcplus$questTotalRows;
    @Unique
    private StyledText cnpcplus$lastText;
    @Unique private java.util.List<StyledText.Line> cnpcplus$questLines;
    @Shadow private IQuest quest;

    @Unique
    private int cnpcplus$visibleRows() {
        return Math.max(1, (this.imageHeight - CNPCPLUS_TEXT_TOP - CNPCPLUS_TEXT_BOTTOM_MARGIN) / CNPCPLUS_LINE_HEIGHT);
    }

    @Unique
    private int cnpcplus$textTop() {
        return this.guiTop + CNPCPLUS_TEXT_TOP;
    }

    @Unique
    private int cnpcplus$textBottom() {
        return this.guiTop + this.imageHeight - CNPCPLUS_TEXT_BOTTOM_MARGIN;
    }

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void cnpcplus$pages(CallbackInfo ci) {
        addButton(new GuiButtonNop(this, 480, guiLeft + 4, guiTop + imageHeight - 24, 26, 20, "<"));
        addButton(new GuiButtonNop(this, 481, guiLeft + 146, guiTop + imageHeight - 24, 26, 20, ">"));
    }

    /**
     * 逐行只做计数、过滤和裁剪，绝不在这里 clamp 行起点：
     * 画第一行时总行数还只有 1，若此时 clamp 会把滚动位置每帧归零，
     * 表现就是滑条能画出来但点击和滚轮下一帧就被撤销。
     */
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
        graphics.enableScissor(this.guiLeft + 4, this.cnpcplus$textTop(), this.guiLeft + 166, this.cnpcplus$textBottom());
        try {
            for (int row = 0; row < visible && row + cnpcplus$questRowStart < cnpcplus$questTotalRows; row++)
                StyledTextRender.draw(graphics, cnpcplus$questLines.get(row + cnpcplus$questRowStart).component(),
                        guiLeft + 4, cnpcplus$textTop() + row * CNPCPLUS_LINE_HEIGHT, CustomNpcResourceListener.DefaultTextColor, true);
        } finally {
            graphics.disableScissor();
        }
        if (getButton(480) != null) getButton(480).visible = cnpcplus$questRowStart > 0;
        if (getButton(481) != null) getButton(481).visible = cnpcplus$questRowStart + visible < cnpcplus$questTotalRows;
        ci.cancel();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/client/gui/player/GuiQuestCompletion;drawQuestText(Lnet/minecraft/client/gui/GuiGraphics;)V",
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void cnpcplus$drawScrollbar(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        this.cnpcplus$questRowStart = TextScrollBar.clamp(
                this.cnpcplus$questRowStart, this.cnpcplus$questTotalRows, this.cnpcplus$visibleRows());
        TextScrollBar.draw(graphics, this.guiLeft + 168, this.cnpcplus$textTop(), this.cnpcplus$textBottom(),
                this.cnpcplus$questTotalRows, this.cnpcplus$visibleRows(), this.cnpcplus$questRowStart);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = this.cnpcplus$textTop();
        int bottom = this.cnpcplus$textBottom();
        if (button == 0 && this.cnpcplus$questTotalRows > this.cnpcplus$visibleRows()
                && mouseX >= this.guiLeft + 166 && mouseX <= this.guiLeft + 172
                && mouseY >= top && mouseY <= bottom) {
            this.cnpcplus$questRowStart = TextScrollBar.rowForMouse((int) mouseY, top, bottom,
                    this.cnpcplus$questTotalRows, this.cnpcplus$visibleRows());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        // 1.21.1 竖直增量在第四个参数；横向滚轮设备只给 scrollX 时退回它。
        double delta = scrollY != 0.0 ? scrollY : scrollX;
        if (delta == 0.0) return false;
        int top = this.cnpcplus$textTop();
        int bottom = this.cnpcplus$textBottom();
        if (mouseX < this.guiLeft + 4 || mouseX > this.guiLeft + 170 || mouseY < top || mouseY > bottom) {
            return false;
        }
        int visible = this.cnpcplus$visibleRows();
        if (this.cnpcplus$questTotalRows <= visible) return false;
        this.cnpcplus$questRowStart = TextScrollBar.clamp(
                this.cnpcplus$questRowStart + (delta > 0.0 ? -3 : 3),
                this.cnpcplus$questTotalRows, visible);
        return true;
    }
}
