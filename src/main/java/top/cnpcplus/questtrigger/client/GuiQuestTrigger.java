package top.cnpcplus.questtrigger.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.client.gui.select.GuiQuestSelection;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.GuiSelectionListener;
import top.cnpcplus.questtrigger.TileQuestTrigger;
import top.cnpcplus.questtrigger.network.PacketSaveTriggerData;

public class GuiQuestTrigger extends GuiNPCInterface implements GuiSelectionListener {
    private static final int WHITE = 0xFFFFFF, GOLD = 0xFFB400, HIGHLIGHT = 0xFFE27A;
    private final BlockPos pos;
    private int mode, interval, areaMode, sizeX, sizeY, sizeZ, radius, questId;
    private String questTitle = "";

    public GuiQuestTrigger(BlockPos pos, CompoundTag data) {
        this.pos = pos;
        CompoundTag d = data.getCompound(TileQuestTrigger.TAG);
        mode = d.getInt("mode");
        interval = Math.max(1, d.getInt("interval"));
        areaMode = d.getInt("areaMode");
        sizeX = Math.max(0, d.getInt("sizeX"));
        sizeY = Math.max(0, d.getInt("sizeY"));
        sizeZ = Math.max(0, d.getInt("sizeZ"));
        radius = Math.max(0, d.getInt("radius"));
        questId = d.getInt("questId");
        if (questId > 0) {
            var quest = QuestController.instance != null ? QuestController.instance.quests.get(questId) : null;
            questTitle = quest != null && quest.title != null ? quest.title : "ID:" + questId;
        }
        drawDefaultBackground = false;
        title = "";
        background = ResourceLocation.parse("cnpcplus:textures/gui/quest_trigger_bg.png");
        imageWidth = 366;
        imageHeight = 226;
    }

    @Override public void init() {
        super.init();
        GuiLabel titleLabel = new GuiLabel(8, Component.translatable("cnpcplus.questtrigger.title"), GOLD, guiLeft, guiTop + 9, 366, 0);
        titleLabel.setCentered(true);
        addLabel(titleLabel);
        addLabel(new GuiLabel(1, Component.translatable("cnpcplus.questtrigger.mode"), WHITE, guiLeft + 12, guiTop + 34, 40, 0));
        addButton(new GuiButtonNop(this, 1, guiLeft + 120, guiTop + 28, 150, 20,
                new String[]{I18n.get("cnpcplus.questtrigger.redstone"), I18n.get("cnpcplus.questtrigger.always")}, mode));
        if (mode == 1) {
            addLabel(new GuiLabel(2, Component.translatable("cnpcplus.questtrigger.interval"), WHITE, guiLeft + 12, guiTop + 60, 40, 0));
            addTextField(new GuiTextFieldNop(2, this, guiLeft + 120, guiTop + 56, 60, 20, interval + ""));
            getTextField(2).numbersOnly = true;
            getTextField(2).setMinMaxDefault(1, 600, 20);
        }
        addLabel(new GuiLabel(3, Component.translatable("cnpcplus.questtrigger.range"), WHITE, guiLeft + 12, guiTop + 92, 40, 0));
        addButton(new GuiButtonNop(this, 3, guiLeft + 120, guiTop + 86, 150, 20,
                new String[]{I18n.get("cnpcplus.questtrigger.box"), I18n.get("cnpcplus.questtrigger.sphere")}, areaMode));
        if (areaMode == 0) {
            addLabel(new GuiLabel(4, "X", guiLeft + 104, guiTop + 118, WHITE));
            addTextField(new GuiTextFieldNop(4, this, guiLeft + 120, guiTop + 114, 56, 20, sizeX + ""));
            getTextField(4).numbersOnly = true;
            getTextField(4).setMinMaxDefault(0, 100, 3);
            addLabel(new GuiLabel(5, "Y", guiLeft + 176, guiTop + 118, WHITE));
            addTextField(new GuiTextFieldNop(5, this, guiLeft + 192, guiTop + 114, 56, 20, sizeY + ""));
            getTextField(5).numbersOnly = true;
            getTextField(5).setMinMaxDefault(0, 100, 3);
            addLabel(new GuiLabel(6, "Z", guiLeft + 248, guiTop + 118, WHITE));
            addTextField(new GuiTextFieldNop(6, this, guiLeft + 264, guiTop + 114, 56, 20, sizeZ + ""));
            getTextField(6).numbersOnly = true;
            getTextField(6).setMinMaxDefault(0, 100, 3);
        } else {
            addLabel(new GuiLabel(7, Component.translatable("cnpcplus.questtrigger.radius"), WHITE, guiLeft + 12, guiTop + 118, 40, 0));
            addTextField(new GuiTextFieldNop(7, this, guiLeft + 120, guiTop + 114, 60, 20, radius + ""));
            getTextField(7).numbersOnly = true;
            getTextField(7).setMinMaxDefault(0, 100, 3);
        }
        addLabel(new GuiLabel(9, Component.translatable("cnpcplus.questtrigger.quest"), WHITE, guiLeft + 12, guiTop + 152, 40, 0));
        String name = questTitle.isEmpty() ? "ID:" + questId : questTitle;
        if (name.length() > 21) name = name.substring(0, 21) + "...";
        String selected = questId > 0 ? I18n.get("cnpcplus.questtrigger.questBound", name) : I18n.get("cnpcplus.questtrigger.questNone");
        addLabel(new GuiLabel(10, selected, guiLeft + 120, guiTop + 152, questId > 0 ? HIGHLIGHT : WHITE));
        addButton(new GuiButtonNop(this, 9, guiLeft + 292, guiTop + 148, 60, 20, I18n.get("cnpcplus.questtrigger.select")));
        addButton(new GuiButtonNop(this, 11, guiLeft + 12, guiTop + 196, 120, 20,
                I18n.get(QuestTriggerOverlay.isVisible() ? "cnpcplus.questtrigger.overlayOn" : "cnpcplus.questtrigger.overlayOff")));
        addButton(new GuiButtonNop(this, 0, guiLeft + 250, guiTop + 196, 104, 20, I18n.get("cnpcplus.questtrigger.done")));
    }

    @Override public void buttonEvent(GuiButtonNop button) {
        readFields();
        if (button.id == 0) { save(); close(); }
        if (button.id == 1) { mode = button.getValue(); init(); }
        if (button.id == 3) { areaMode = button.getValue(); init(); }
        if (button.id == 9) { save(); setSubGui(new GuiQuestSelection(questId)); }
        if (button.id == 11) {
            if (QuestTriggerOverlay.isVisible()) QuestTriggerOverlay.hide();
            else QuestTriggerOverlay.show(pos, areaMode, sizeX, sizeY, sizeZ, radius);
            init();
        }
    }

    @Override public void selected(int id, String title) {
        questId = id;
        questTitle = title != null ? title : "";
        if (questId > 0) {
            var quest = QuestController.instance != null ? QuestController.instance.quests.get(questId) : null;
            if (quest != null && quest.title != null) questTitle = quest.title;
        }
        init();
    }

    private void readFields() {
        if (mode == 1 && getTextField(2) != null) interval = getTextField(2).getInteger();
        if (areaMode == 0) {
            if (getTextField(4) != null) sizeX = getTextField(4).getInteger();
            if (getTextField(5) != null) sizeY = getTextField(5).getInteger();
            if (getTextField(6) != null) sizeZ = getTextField(6).getInteger();
        } else if (getTextField(7) != null) radius = getTextField(7).getInteger();
    }

    public void syncOverlay() {
        readFields();
        QuestTriggerOverlay.updateData(pos, areaMode, sizeX, sizeY, sizeZ, radius);
    }

    @Override public void save() {
        readFields();
        CompoundTag d = new CompoundTag();
        d.putInt("mode", mode); d.putInt("interval", interval); d.putInt("areaMode", areaMode);
        d.putInt("sizeX", sizeX); d.putInt("sizeY", sizeY); d.putInt("sizeZ", sizeZ);
        d.putInt("radius", radius); d.putInt("questId", questId);
        CompoundTag data = new CompoundTag();
        data.put(TileQuestTrigger.TAG, d);
        PacketDistributor.sendToServer(new PacketSaveTriggerData(pos, data));
    }
}
