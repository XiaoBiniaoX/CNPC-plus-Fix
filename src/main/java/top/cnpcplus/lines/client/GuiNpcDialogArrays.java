package top.cnpcplus.lines.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.select.GuiDialogSelection;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketMenuGet;
import noppes.npcs.packets.server.SPacketMenuSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.listeners.GuiSelectionListener;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import top.cnpcplus.lines.DialogArrays;

public final class GuiNpcDialogArrays extends GuiNPCInterface2 implements ICustomScrollListener, IGuiData, GuiSelectionListener {
    private final List<DialogOption> entries = new ArrayList<>();
    private int selected = -1;
    private boolean edited;
    public GuiNpcDialogArrays(EntityNPCInterface npc) { super(npc); load(); Packets.sendServer(new SPacketMenuGet(EnumMenuType.ADVANCED)); }
    private void load() {
        entries.clear();
        DialogArrays.ordered(npc.dialogs).forEach(option -> entries.add(DialogArrays.copy(option)));
        selected = entries.isEmpty() ? -1 : Math.max(0, Math.min(selected, entries.size() - 1));
    }
    @Override public void init() {
        super.init();
        addLabel(new GuiLabel(0, "cnpcplus.arrays.dialogs", guiLeft + 4, guiTop + 5));
        GuiCustomScrollNop scroll = new GuiCustomScrollNop(this, 0);
        scroll.disabledSearch();
        scroll.setSize(408, 117);
        scroll.guiLeft = guiLeft + 4;
        scroll.guiTop = guiTop + 18;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) labels.add((i + 1) + ". " + entries.get(i).title + " [" + entries.get(i).dialogId + "]");
        scroll.setUnsortedList(labels);
        scroll.setSelectedIndex(selected);
        addScroll(scroll);
        addButton(new GuiButtonNop(this, 4, guiLeft + 4, guiTop + 141, 408, 20, "dialog.selectoption"));
        getButton(4).setEnabled(selected >= 0);
        addButton(new GuiButtonNop(this, 0, guiLeft + 4, guiTop + 167, 96, 20, "gui.add"));
        addButton(new GuiButtonNop(this, 1, guiLeft + 108, guiTop + 167, 96, 20, "gui.remove"));
        addButton(new GuiButtonNop(this, 2, guiLeft + 212, guiTop + 167, 96, 20, "cnpcplus.arrays.up"));
        addButton(new GuiButtonNop(this, 3, guiLeft + 316, guiTop + 167, 96, 20, "cnpcplus.arrays.down"));
        getButton(1).setEnabled(selected >= 0);
        getButton(2).setEnabled(selected > 0);
        getButton(3).setEnabled(selected >= 0 && selected + 1 < entries.size());
    }
    @Override public void buttonEvent(GuiButtonNop button) {
        switch (button.id) {
            case 0 -> { entries.add(new DialogOption()); selected = entries.size() - 1; }
            case 1 -> { entries.remove(selected); selected = Math.min(selected, entries.size() - 1); }
            case 2, 3 -> {
                int next = selected + (button.id == 2 ? -1 : 1);
                if (selected < 0 || next < 0 || next >= entries.size()) return;
                Collections.swap(entries, selected, next);
                selected = next;
            }
            case 4 -> { edited = true; setSubGui(new GuiDialogSelection(entries.get(selected).dialogId)); return; }
        }
        edited = true;
        init();
    }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) { selected = scroll.getSelectedIndex(); init(); }
    @Override public void scrollDoubleClicked(String label, GuiCustomScrollNop scroll) {
        if (selected >= 0) { edited = true; setSubGui(new GuiDialogSelection(entries.get(selected).dialogId)); }
    }
    @Override public void selected(int id, String name) {
        if (selected < 0 || selected >= entries.size()) return;
        entries.get(selected).dialogId = id;
        entries.get(selected).title = name;
        edited = true;
    }
    @Override public void setGuiData(CompoundTag tag) { npc.advanced.readToNBT(tag); if (!edited) load(); init(); }
    @Override public void save() {
        HashMap<Integer, DialogOption> dialogs = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            DialogOption option = DialogArrays.copy(entries.get(i));
            option.slot = i;
            dialogs.put(i, option);
        }
        npc.dialogs = dialogs;
        Packets.sendServer(new SPacketMenuSave(EnumMenuType.ADVANCED, npc.advanced.save(new CompoundTag())));
    }
}
