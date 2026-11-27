package top.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.constants.EnumQuestRepeat;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import top.cnpcplus.quest.QuestAreaKill;
import top.cnpcplus.quest.QuestCraft;
import top.cnpcplus.quest.QuestExplore;

/** Button-based type/repeat selector for the quest editor. */
public final class SubGuiQuestPicker extends GuiQuestPanel {
    private static final String[] TYPES = {
            "quest.item", "quest.dialog", "quest.kill", "quest.location",
            "quest.areakill", "quest.manual", "cnpcplus.quest.craft", "cnpcplus.quest.explore"
    };
    private static final String[] REPEATS = {
            "gui.no", "gui.yes", "quest.mcdaily", "quest.mcweekly", "quest.rldaily", "quest.rlweekly"
    };
    private final GuiNPCInterface parent;
    private final Quest quest;
    private final boolean repeat;

    public SubGuiQuestPicker(GuiNPCInterface parent, Quest quest, boolean repeat) {
        this.parent = parent;
        this.quest = quest;
        this.repeat = repeat;
        imageWidth = 320;
        imageHeight = repeat ? 208 : 240;
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        String[] choices = repeat ? REPEATS : TYPES;
        for (int i = 0; i < choices.length; i++) {
            int y = guiTop + 30 + i * 23;
            GuiButtonNop choice = new GuiButtonNop(this, i, guiLeft + 12, y, imageWidth - 24, 20, choices[i]);
            if (!repeat) choice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("cnpcplus.quest.type." + i + ".help")));
            addButton(choice);
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (repeat) {
            if (button.id >= 0 && button.id < EnumQuestRepeat.values().length) {
                quest.repeat = EnumQuestRepeat.values()[button.id];
                GuiButtonNop display = parent.getButton(108);
                if (display != null) display.setDisplayText(REPEATS[button.id]);
            }
        } else if (button.id >= 0 && button.id < TYPES.length) {
            quest.setType(button.id);
            if (button.id == 4) quest.questInterface = new QuestAreaKill();
            else if (button.id == 6) quest.questInterface = new QuestCraft();
            else if (button.id == 7) quest.questInterface = new QuestExplore();
            quest.questInterface.questId = quest.id;
            GuiButtonNop display = parent.getButton(106);
            if (display != null) display.setDisplayText(TYPES[button.id]);
        }
        close();
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, I18n.get(repeat ? "cnpcplus.quest.repeat.title" : "cnpcplus.quest.type.title"),
                guiLeft + imageWidth / 2, guiTop + 10, 0x202020);
        graphics.drawCenteredString(font, I18n.get(repeat ? "cnpcplus.quest.repeat.help" : "cnpcplus.quest.type.help"),
                guiLeft + imageWidth / 2, guiTop + 20, 0x686868);
    }
}
