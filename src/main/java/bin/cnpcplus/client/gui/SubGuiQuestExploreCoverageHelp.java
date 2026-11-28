package bin.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.util.GuiNpcButton;

/** The same coverage explanation page as the modern quest editors. */
public final class SubGuiQuestExploreCoverageHelp extends GuiQuestPanel {
    public SubGuiQuestExploreCoverageHelp() {
        xSize = 380;
        ySize = 300;
        closeOnEsc = true;
    }

    @Override public void initGui() {
        super.initGui();
        addButton(new GuiNpcButton(0, guiLeft + xSize - 86, guiTop + ySize - 28, 74, 20, "gui.done"));
    }

    @Override public void buttonEvent(GuiButton button) { if (button.id == 0) close(); }

    @Override protected void drawPanelContents(int mouseX, int mouseY, float partialTicks) {
        fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.coverage.help.title"), guiLeft + 12, guiTop + 12, 0x202020);
        fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.coverage.volume.title"), guiLeft + 18, guiTop + 36, 0x303030);
        fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.explore.coverage.volume.help"), guiLeft + 18, guiTop + 50, xSize - 36, 0x555555);
        fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.coverage.horizontal.title"), guiLeft + 18, guiTop + 126, 0x303030);
        fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.explore.coverage.horizontal.help"), guiLeft + 18, guiTop + 140, xSize - 36, 0x555555);
        fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.explore.coverage.common.help"), guiLeft + 18, guiTop + 218, xSize - 36, 0x686868);
    }
}
