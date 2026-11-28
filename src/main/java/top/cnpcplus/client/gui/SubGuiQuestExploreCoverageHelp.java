package top.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;

/** Explains the two exploration coverage modes without changing the current quest draft. */
public final class SubGuiQuestExploreCoverageHelp extends GuiQuestPanel {
    public SubGuiQuestExploreCoverageHelp() {
        imageWidth = 380;
        imageHeight = 300;
    }

    @Override
    public void init() {
        super.init();
        addButton(new GuiButtonNop(this, 0, guiLeft + imageWidth - 86, guiTop + imageHeight - 28,
                74, 20, "gui.done"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 0) close();
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderPanel(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, I18n.get("cnpcplus.quest.explore.coverage.help.title"),
                guiLeft + 12, guiTop + 12, 0x202020, false);
        graphics.drawString(font, I18n.get("cnpcplus.quest.explore.coverage.volume.title"),
                guiLeft + 18, guiTop + 36, 0x303030, false);
        graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.explore.coverage.volume.help"),
                guiLeft + 18, guiTop + 50, imageWidth - 36, 0x555555);
        graphics.drawString(font, I18n.get("cnpcplus.quest.explore.coverage.horizontal.title"),
                guiLeft + 18, guiTop + 126, 0x303030, false);
        graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.explore.coverage.horizontal.help"),
                guiLeft + 18, guiTop + 140, imageWidth - 36, 0x555555);
        graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.explore.coverage.common.help"),
                guiLeft + 18, guiTop + 218, imageWidth - 36, 0x686868);
    }
}
