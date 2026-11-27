package top.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.QuestCraft;

final class SubGuiQuestCraftSource extends GuiNPCInterface {
    private static final String[] KEYS = {
            "cnpcplus.quest.source.workbench", "cnpcplus.quest.source.carpentry",
            "cnpcplus.quest.source.anvil", "cnpcplus.quest.source.smithing",
            "cnpcplus.quest.source.brewing", "cnpcplus.quest.source.campfire",
            "cnpcplus.quest.source.furnace", "cnpcplus.quest.source.drop"
    };
    private final QuestCraft craft;
    private final int slot;

    SubGuiQuestCraftSource(QuestCraft craft, int slot) {
        this.craft = craft;
        this.slot = slot;
        imageWidth = 230;
        imageHeight = 212;
        setBackground("menubg.png");
    }

    @Override public void m_7856_() {
        super.m_7856_();
        for (int i = 0; i < KEYS.length; i++)
            addButton(new GuiButtonNop(this, i, guiLeft + 10, guiTop + 30 + i * 22, imageWidth - 20, 20, I18n.get(KEYS[i])));
    }

    @Override public void buttonEvent(GuiButtonNop button) {
        if (button.id >= 0 && button.id < ItemProductionEvent.Source.values().length)
            craft.sources[slot] = ItemProductionEvent.Source.values()[button.id];
        close();
    }

    @Override public void m_88315_(GuiGraphics graphics, int x, int y, float partial) {
        super.m_88315_(graphics, x, y, partial);
        graphics.drawCenteredString(font, I18n.get("cnpcplus.quest.craft.source.title"), guiLeft + imageWidth / 2, guiTop + 10, 0x202020);
    }
}
