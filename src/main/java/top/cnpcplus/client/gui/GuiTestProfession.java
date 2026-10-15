package top.cnpcplus.client.gui;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketNpcJobSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import top.cnpcplus.roles.JobTestProfession;

/**
 * 测试职业配置界面（最小占位）。
 */
public class GuiTestProfession extends GuiNPCInterface2 implements ITextfieldListener {

    private final JobTestProfession job;

    public GuiTestProfession(EntityNPCInterface npc) {
        super(npc);
        this.job = (JobTestProfession) npc.job;
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        int y = this.guiTop + 20;
        this.addLabel(new GuiLabel(0, "测试职业配置", this.guiLeft + 10, y));
        y += 22;
        this.addLabel(new GuiLabel(1, "消息内容:", this.guiLeft + 10, y + 5));
        this.addTextField(new GuiTextFieldNop(0, this, this.guiLeft + 80, y, 200, 20, this.job.testMessage));
        y += 30;
        this.addButton(new GuiButtonNop(this, 1, this.guiLeft + 10, y, 60, 20, "gui.done"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 1) {
            this.save();
            this.onClose();
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        if (textfield.id == 0) {
            this.job.testMessage = textfield.getValue();
        }
    }

    @Override
    public void save() {
        this.job.testMessage = this.getTextField(0).getValue();
        Packets.sendServer(new SPacketNpcJobSave(this.job.save(new CompoundTag())));
    }
}
