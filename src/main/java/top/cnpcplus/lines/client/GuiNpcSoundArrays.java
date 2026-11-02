package top.cnpcplus.lines.client;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketMenuSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import top.cnpcplus.lines.NpcSoundArrays;

public final class GuiNpcSoundArrays extends GuiNPCInterface2 {
    private static final String[] LABELS = {"advanced.idlesound", "advanced.angersound", "advanced.hurtsound", "advanced.deathsound", "advanced.stepsound"};
    public GuiNpcSoundArrays(EntityNPCInterface npc) { super(npc); }
    @Override public void init() {
        super.init();
        NpcSoundArrays sounds = NpcSoundArrays.get(npc.advanced);
        for (int i = 0; i < 5; i++) {
            int y = guiTop + 12 + i * 33;
            addLabel(new GuiLabel(i, LABELS[i], guiLeft + 4, y + 5));
            addButton(new GuiButtonNop(this, i, guiLeft + 88, y, 325, 20,
                    net.minecraft.client.resources.language.I18n.get("cnpcplus.arrays.sounds") + " (" + sounds.slots[i].sounds.size() + ")"));
        }
    }
    @Override public void buttonEvent(GuiButtonNop button) {
        if (button.id < 0 || button.id >= 5) return;
        int slot = button.id;
        setSubGui(new SubGuiSoundArray(NpcSoundArrays.get(npc.advanced).slots[slot], true,
                value -> NpcSoundArrays.get(npc.advanced).slots[slot] = value));
    }
    @Override public void save() { Packets.sendServer(new SPacketMenuSave(EnumMenuType.ADVANCED, npc.advanced.save(new CompoundTag()))); }
}
