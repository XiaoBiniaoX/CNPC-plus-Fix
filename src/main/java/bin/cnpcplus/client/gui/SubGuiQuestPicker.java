package bin.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.constants.EnumQuestRepeat;
import noppes.npcs.controllers.data.Quest;

/** Button-based quest type and repeat selector. */
public final class SubGuiQuestPicker extends GuiQuestPanel {
    public static final String[] TYPES = {
            "cnpcplus.quest.type.item", "cnpcplus.quest.type.dialog", "cnpcplus.quest.type.kill",
            "cnpcplus.quest.type.location", "cnpcplus.quest.type.areakill", "cnpcplus.quest.type.manual",
            "cnpcplus.quest.type.craft", "cnpcplus.quest.type.explore"
    };
    public static final String[] REPEATS = {
            "gui.no", "gui.yes", "quest.mcdaily", "quest.mcweekly", "quest.rldaily", "quest.rlweekly"
    };
    private final Quest quest;
    private final boolean repeat;

    public SubGuiQuestPicker(Quest quest, boolean repeat) {
        this.quest = quest;
        this.repeat = repeat;
        xSize = 320;
        ySize = repeat ? 208 : 240;
        closeOnEsc = true;
    }

    @Override public void initGui() {
        super.initGui();
        String[] choices = repeat ? REPEATS : TYPES;
        int selected = repeat ? quest.repeat.ordinal() : quest.type;
        for (int i = 0; i < choices.length; i++) {
            GuiNpcButton button = new GuiChoiceButton(i, guiLeft + 12, guiTop + 30 + i * 23, xSize - 24, 20, choices[i], i == selected);
            addButton(button);
        }
    }

    @Override public void buttonEvent(GuiButton button) {
        String[] choices = repeat ? REPEATS : TYPES;
        if (button.id < 0 || button.id >= choices.length) return;
        if (repeat) quest.repeat = EnumQuestRepeat.values()[button.id];
        else if (quest.type != button.id) quest.setType(button.id);
        if (!repeat && quest.questInterface != null) quest.questInterface.questId = quest.id;
        close();
    }

    @Override protected void drawPanelContents(int mouseX, int mouseY, float partialTicks) {
        drawCenteredString(fontRenderer,
                I18n.translateToLocal(repeat ? "cnpcplus.quest.repeat.title" : "cnpcplus.quest.type.title"),
                 guiLeft + xSize / 2, guiTop + 10, 0x202020);
        drawCenteredString(fontRenderer,
                I18n.translateToLocal(repeat ? "cnpcplus.quest.repeat.help" : "cnpcplus.quest.type.help"),
                 guiLeft + xSize / 2, guiTop + 20, 0x686868);
    }
}
