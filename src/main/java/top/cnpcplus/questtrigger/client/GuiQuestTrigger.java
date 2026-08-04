package top.cnpcplus.questtrigger.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
        GuiLabel titleLabel = new GuiLabel(8, "区域任务触发器", this.guiLeft, this.guiTop + 9, GOLD, 366, 0);
        titleLabel.setCentered(true);
        this.addLabel(titleLabel);

        this.addLabel(new GuiLabel(1, "激活方式", this.guiLeft + 12, this.guiTop + 34, WHITE));
        this.addButton(new GuiButtonNop(this, 1, this.guiLeft + 120, this.guiTop + 28, 150, 20,
                new String[]{"红石激活", "始终检查"}, this.mode));
        if (this.mode == 1) {
            this.addLabel(new GuiLabel(2, "检查间隔(tick)", this.guiLeft + 12, this.guiTop + 60, WHITE));
            this.addTextField(new GuiTextFieldNop(2, this, this.guiLeft + 120, this.guiTop + 56, 60, 20, this.interval + ""));
            this.getTextField(2).numbersOnly = true;
            this.getTextField(2).setMinMaxDefault(1, 600, 20);
        }

        this.addLabel(new GuiLabel(3, "触发范围", this.guiLeft + 12, this.guiTop + 92, WHITE));
        this.addButton(new GuiButtonNop(this, 3, this.guiLeft + 120, this.guiTop + 86, 150, 20,
                new String[]{"区域(长方体)", "范围(球形)"}, this.areaMode));
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
            this.addLabel(new GuiLabel(7, "半径(格)", this.guiLeft + 12, this.guiTop + 118, WHITE));
            this.addTextField(new GuiTextFieldNop(7, this, this.guiLeft + 120, this.guiTop + 114, 60, 20, this.radius + ""));
            this.getTextField(7).numbersOnly = true;
            this.getTextField(7).setMinMaxDefault(0, 100, 3);
        }

        this.addLabel(new GuiLabel(9, "绑定任务", this.guiLeft + 12, this.guiTop + 152, WHITE));
        String title = this.questId > 0
                ? "任务: " + truncate(this.questTitle.isEmpty() ? ("ID:" + this.questId) : this.questTitle, 21)
                : "任务: 未选择";
        this.addLabel(new GuiLabel(10, title, this.guiLeft + 120, this.guiTop + 152, this.questId > 0 ? HIGHLIGHT : WHITE));
        this.addButton(new GuiButtonNop(this, 9, this.guiLeft + 292, this.guiTop + 148, 60, 20, "选择"));
        this.addButton(new GuiButtonNop(this, 11, this.guiLeft + 12, this.guiTop + 196, 120, 20,
                QuestTriggerOverlay.isVisible() ? "区域显示: 开" : "区域显示: 关"));
        this.addButton(new GuiButtonNop(this, 0, this.guiLeft + 250, this.guiTop + 196, 104, 20, "完成"));
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
