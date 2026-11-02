package bin.cnpcplus.lines.client;

import bin.cnpcplus.lines.DialogArrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.select.GuiDialogSelection;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.entity.EntityNPCInterface;

public final class GuiNpcDialogArrays extends GuiNPCInterface2 implements ICustomScrollListener, IGuiData, GuiSelectionListener {
    private final List<DialogOption> entries = new ArrayList<>();
    private int selected = -1;
    private boolean edited;

    public GuiNpcDialogArrays(EntityNPCInterface npc) {
        super(npc); load();
        Client.sendData(EnumPacketServer.MainmenuAdvancedGet);
    }
    private void load() {
        entries.clear();
        for (DialogOption option : DialogArrays.ordered(npc.dialogs)) entries.add(DialogArrays.copy(option));
        selected = entries.isEmpty() ? -1 : Math.max(0, Math.min(selected, entries.size() - 1));
    }
    @Override public void initGui() {
        super.initGui();
        addLabel(new GuiNpcLabel(0, "cnpcplus.arrays.dialogs", guiLeft + 4, guiTop + 5));
        GuiCustomScroll scroll = new GuiCustomScroll(this, 0);
        scroll.setSize(408, 117); scroll.guiLeft = guiLeft + 4; scroll.guiTop = guiTop + 18;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) labels.add((i + 1) + ". " + entries.get(i).title + " [" + entries.get(i).dialogId + "]");
        scroll.setUnsortedList(labels); scroll.selected = selected; addScroll(scroll);
        addButton(new GuiNpcButton(4, guiLeft + 4, guiTop + 141, 408, 20, "dialog.selectoption"));
        getButton(4).setEnabled(selected >= 0);
        addButton(new GuiNpcButton(0, guiLeft + 4, guiTop + 167, 96, 20, "gui.add"));
        addButton(new GuiNpcButton(1, guiLeft + 108, guiTop + 167, 96, 20, "gui.remove"));
        addButton(new GuiNpcButton(2, guiLeft + 212, guiTop + 167, 96, 20, "cnpcplus.arrays.up"));
        addButton(new GuiNpcButton(3, guiLeft + 316, guiTop + 167, 96, 20, "cnpcplus.arrays.down"));
        getButton(1).setEnabled(selected >= 0); getButton(2).setEnabled(selected > 0);
        getButton(3).setEnabled(selected >= 0 && selected + 1 < entries.size());
    }
    @Override public void buttonEvent(GuiButton button) {
        switch (button.id) {
            case 0: entries.add(new DialogOption()); selected = entries.size() - 1; break;
            case 1: entries.remove(selected); selected = Math.min(selected, entries.size() - 1); break;
            case 2: case 3:
                int next = selected + (button.id == 2 ? -1 : 1);
                Collections.swap(entries, selected, next); selected = next; break;
            case 4: edited = true; setSubGui(new GuiDialogSelection(entries.get(selected).dialogId)); return;
            default: return;
        }
        edited = true; initGui();
    }
    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll scroll) {
        selected = scroll.selected; initGui();
    }
    @Override public void scrollDoubleClicked(String label, GuiCustomScroll scroll) {
        if (selected >= 0) { edited = true; setSubGui(new GuiDialogSelection(entries.get(selected).dialogId)); }
    }
    @Override public void selected(int id, String name) {
        entries.get(selected).dialogId = id; entries.get(selected).title = name; edited = true;
    }
    @Override public void setGuiData(NBTTagCompound tag) {
        npc.advanced.readToNBT(tag); if (!edited) load(); initGui();
    }
    @Override public void save() {
        HashMap<Integer, DialogOption> dialogs = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            DialogOption option = DialogArrays.copy(entries.get(i)); option.slot = i; dialogs.put(i, option);
        }
        npc.dialogs = dialogs;
        Client.sendData(EnumPacketServer.MainmenuAdvancedSave, npc.advanced.writeToNBT(new NBTTagCompound()));
    }
}
