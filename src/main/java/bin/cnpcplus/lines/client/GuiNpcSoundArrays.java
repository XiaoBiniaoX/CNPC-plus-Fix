package bin.cnpcplus.lines.client;

import bin.cnpcplus.lines.NpcSoundArrays;
import bin.cnpcplus.lines.SoundArray;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.entity.EntityNPCInterface;

public final class GuiNpcSoundArrays extends GuiNPCInterface2 implements IGuiData {
    private final SoundArray[] draft = new SoundArray[5];
    private boolean edited;

    public GuiNpcSoundArrays(EntityNPCInterface npc) {
        super(npc); load();
        Client.sendData(EnumPacketServer.MainmenuAdvancedGet);
    }
    private void load() {
        NpcSoundArrays value = NpcSoundArrays.get(npc.advanced);
        for (int i = 0; i < draft.length; i++) draft[i] = value.slots[i].copy();
    }
    @Override public void initGui() {
        super.initGui();
        String[] labels = {"advanced.idlesound", "advanced.angersound", "advanced.hurtsound", "advanced.deathsound", "advanced.stepsound"};
        for (int i = 0; i < draft.length; i++) {
            addLabel(new GuiNpcLabel(i, labels[i], guiLeft + 6, guiTop + 24 + i * 31));
            addButton(new GuiNpcButton(i, guiLeft + 94, guiTop + 19 + i * 31, 310, 20,
                    draft[i].sounds.size() + " · " + draft[i].first()));
        }
        addLabel(new GuiNpcLabel(5, "cnpcplus.arrays.soundHint", guiLeft + 6, guiTop + 183));
    }
    @Override public void buttonEvent(GuiButton button) {
        if (button.id < 0 || button.id >= draft.length) return;
        edited = true;
        final int slot = button.id;
        setSubGui(new SubGuiSoundArray(draft[slot], true, value -> {
            draft[slot] = value; getButton(slot).displayString = value.sounds.size() + " · " + value.first();
        }));
    }
    @Override public void setGuiData(NBTTagCompound tag) {
        npc.advanced.readToNBT(tag);
        if (!edited) load();
        initGui();
    }
    @Override public void save() {
        NpcSoundArrays value = NpcSoundArrays.get(npc.advanced);
        for (int i = 0; i < draft.length; i++) value.slots[i] = draft[i].copy();
        Client.sendData(EnumPacketServer.MainmenuAdvancedSave, npc.advanced.writeToNBT(new NBTTagCompound()));
    }
}
