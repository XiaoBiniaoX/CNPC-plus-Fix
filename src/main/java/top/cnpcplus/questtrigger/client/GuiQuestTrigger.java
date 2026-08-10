package top.cnpcplus.questtrigger.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.client.gui.select.GuiQuestSelection;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.QuestController;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.GuiSelectionListener;
import top.cnpcplus.questtrigger.TileQuestTrigger;
import top.cnpcplus.questtrigger.network.PacketSaveTriggerData;
import top.cnpcplus.questtrigger.network.TriggerPacketHandler;

public class GuiQuestTrigger extends GuiNPCInterface implements GuiSelectionListener {

    private static final int WHITE = 0xFFFFFF;
    private static final int GOLD = 0xFFB400;
    private static final int HIGHLIGHT = 0xFFE27A;

    private final BlockPos pos;
    private int mode;
    private int interval;
    private int areaMode;
    private int sizeX, sizeY, sizeZ;
    private int radius;
    private int questId;
    private String questTitle = "";

    public GuiQuestTrigger(BlockPos pos, CompoundTag data) {
        this.pos = pos;
        CompoundTag d = data.getCompound(TileQuestTrigger.TAG);
        this.mode = d.getInt("mode");
        this.interval = Math.max(1, d.getInt("interval"));
        this.areaMode = d.getInt("areaMode");
        this.sizeX = Math.max(0, d.getInt("sizeX"));
        this.sizeY = Math.max(0, d.getInt("sizeY"));
        this.sizeZ = Math.max(0, d.getInt("sizeZ"));
        this.radius = Math.max(0, d.getInt("radius"));
        this.questId = d.getInt("questId");
        if (this.questId > 0) {
            var quest = QuestController.instance != null ? QuestController.instance.quests.get(this.questId) : null;
            if (quest != null && quest.title != null) {
                this.questTitle = quest.title;
            } else {
                this.questTitle = "ID:" + this.questId;
            }
        }
        this.drawDefaultBackground = false;
        this.title = "";
        this.background = new ResourceLocation("cnpcplus", "textures/gui/quest_trigger_bg.png");
        this.imageWidth = 366;
        this.imageHeight = 226;
    }

    @Override
    public void init() {
        super.init();
        GuiLabel titleLabel = new GuiLabel(8, Component.translatable("cnpcplus.questtrigger.title"), GOLD, this.guiLeft, this.guiTop + 9, 366, 0);
        titleLabel.setCentered(true);
        this.addLabel(titleLabel);

        this.addLabel(new GuiLabel(1, Component.translatable("cnpcplus.questtrigger.mode"), WHITE, this.guiLeft + 12, this.guiTop + 34, 40, 0));
        this.addButton(new GuiButtonNop(this, 1, this.guiLeft + 120, this.guiTop + 28, 150, 20,
                new String[]{I18n.get("cnpcplus.questtrigger.redstone"), I18n.get("cnpcplus.questtrigger.always")}, this.mode));
        if (this.mode == 1) {
            this.addLabel(new GuiLabel(2, Component.translatable("cnpcplus.questtrigger.interval"), WHITE, this.guiLeft + 12, this.guiTop + 60, 40, 0));
            this.addTextField(new GuiTextFieldNop(2, this, this.guiLeft + 120, this.guiTop + 56, 60, 20, this.interval + ""));
            this.getTextField(2).numbersOnly = true;
            this.getTextField(2).setMinMaxDefault(1, 600, 20);
        }

        this.addLabel(new GuiLabel(3, Component.translatable("cnpcplus.questtrigger.range"), WHITE, this.guiLeft + 12, this.guiTop + 92, 40, 0));
        this.addButton(new GuiButtonNop(this, 3, this.guiLeft + 120, this.guiTop + 86, 150, 20,
                new String[]{I18n.get("cnpcplus.questtrigger.box"), I18n.get("cnpcplus.questtrigger.sphere")}, this.areaMode));
        if (this.areaMode == 0) {
            this.addLabel(new GuiLabel(4, "X", this.guiLeft + 104, this.guiTop + 118, WHITE));
            this.addTextField(new GuiTextFieldNop(4, this, this.guiLeft + 120, this.guiTop + 114, 56, 20, this.sizeX + ""));
            this.getTextField(4).numbersOnly = true;
            this.getTextField(4).setMinMaxDefault(0, 100, 3);
            this.addLabel(new GuiLabel(5, "Y", this.guiLeft + 176, this.guiTop + 118, WHITE));
            this.addTextField(new GuiTextFieldNop(5, this, this.guiLeft + 192, this.guiTop + 114, 56, 20, this.sizeY + ""));
            this.getTextField(5).numbersOnly = true;
            this.getTextField(5).setMinMaxDefault(0, 100, 3);
            this.addLabel(new GuiLabel(6, "Z", this.guiLeft + 248, this.guiTop + 118, WHITE));
            this.addTextField(new GuiTextFieldNop(6, this, this.guiLeft + 264, this.guiTop + 114, 56, 20, this.sizeZ + ""));
            this.getTextField(6).numbersOnly = true;
            this.getTextField(6).setMinMaxDefault(0, 100, 3);
        } else {
            this.addLabel(new GuiLabel(7, Component.translatable("cnpcplus.questtrigger.radius"), WHITE, this.guiLeft + 12, this.guiTop + 118, 40, 0));
            this.addTextField(new GuiTextFieldNop(7, this, this.guiLeft + 120, this.guiTop + 114, 60, 20, this.radius + ""));
            this.getTextField(7).numbersOnly = true;
            this.getTextField(7).setMinMaxDefault(0, 100, 3);
        }

        this.addLabel(new GuiLabel(9, Component.translatable("cnpcplus.questtrigger.quest"), WHITE, this.guiLeft + 12, this.guiTop + 152, 40, 0));
        String title = this.questId > 0
                ? I18n.get("cnpcplus.questtrigger.questBound", truncate(this.questTitle.isEmpty() ? ("ID:" + this.questId) : this.questTitle, 21))
                : I18n.get("cnpcplus.questtrigger.questNone");
        this.addLabel(new GuiLabel(10, title, this.guiLeft + 120, this.guiTop + 152, this.questId > 0 ? HIGHLIGHT : WHITE));
        this.addButton(new GuiButtonNop(this, 9, this.guiLeft + 292, this.guiTop + 148, 60, 20, I18n.get("cnpcplus.questtrigger.select")));
        this.addButton(new GuiButtonNop(this, 11, this.guiLeft + 12, this.guiTop + 196, 120, 20,
                QuestTriggerOverlay.isVisible() ? I18n.get("cnpcplus.questtrigger.overlayOn") : I18n.get("cnpcplus.questtrigger.overlayOff")));
        this.addButton(new GuiButtonNop(this, 0, this.guiLeft + 250, this.guiTop + 196, 104, 20, I18n.get("cnpcplus.questtrigger.done")));
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 0) {
            this.save();
            this.close();
        }
        if (button.id == 1) {
            this.mode = button.getValue();
            this.init();
        }
        if (button.id == 3) {
            this.areaMode = button.getValue();
            this.init();
        }
        if (button.id == 9) {
            this.save();
            this.setSubGui(new GuiQuestSelection(this.questId));
        }
        if (button.id == 11) {
            this.readFields();
            if (QuestTriggerOverlay.isVisible()) {
                QuestTriggerOverlay.hide();
            } else {
                QuestTriggerOverlay.show(this.pos, this.areaMode, this.sizeX, this.sizeY, this.sizeZ, this.radius);
            }
            this.init();
        }
    }

    @Override
    public void selected(int id, String title) {
        this.questId = id;
        this.questTitle = title != null ? title : "";
        if (this.questId > 0) {
            var quest = QuestController.instance != null ? QuestController.instance.quests.get(this.questId) : null;
            if (quest != null && quest.title != null) {
                this.questTitle = quest.title;
            }
        }
        this.init();
    }

    private void readFields() {
        try {
            if (this.mode == 1 && this.getTextField(2) != null) {
                this.interval = this.getTextField(2).getInteger();
            }
            if (this.areaMode == 0) {
                if (this.getTextField(4) != null) this.sizeX = this.getTextField(4).getInteger();
                if (this.getTextField(5) != null) this.sizeY = this.getTextField(5).getInteger();
                if (this.getTextField(6) != null) this.sizeZ = this.getTextField(6).getInteger();
            } else if (this.getTextField(7) != null) {
                this.radius = this.getTextField(7).getInteger();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public void syncOverlay() {
        this.readFields();
        QuestTriggerOverlay.updateData(this.pos, this.areaMode, this.sizeX, this.sizeY, this.sizeZ, this.radius);
    }

    @Override
    public void save() {
        this.readFields();

        CompoundTag d = new CompoundTag();
        d.putInt("mode", this.mode);
        d.putInt("interval", this.interval);
        d.putInt("areaMode", this.areaMode);
        d.putInt("sizeX", this.sizeX);
        d.putInt("sizeY", this.sizeY);
        d.putInt("sizeZ", this.sizeZ);
        d.putInt("radius", this.radius);
        d.putInt("questId", this.questId);
        CompoundTag data = new CompoundTag();
        data.put(TileQuestTrigger.TAG, d);
        TriggerPacketHandler.CHANNEL.sendToServer(new PacketSaveTriggerData(this.pos, data));
    }
}
