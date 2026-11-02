package top.cnpcplus.lines.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketMenuGet;
import noppes.npcs.packets.server.SPacketMenuSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import top.cnpcplus.lines.ArrayLine;
import top.cnpcplus.lines.LinesData;
import top.cnpcplus.text.client.StyledTextArea;
import top.cnpcplus.text.client.NativeTextToolbar;

public final class GuiLinesArrayEditor extends GuiNPCInterface2 implements ICustomScrollListener, IGuiData {
    private final int type;
    private final List<ArrayLine> entries = new ArrayList<>();
    private int selected = -1;
    private GuiTextArea text;
    private boolean edited;
    private GuiCustomScrollNop scroll;
    private NativeTextToolbar toolbar;
    public GuiLinesArrayEditor(EntityNPCInterface npc, int type) {
        super(npc);
        this.type = type;
        load();
        Packets.sendServer(new SPacketMenuGet(EnumMenuType.ADVANCED));
    }
    private void load() {
        Lines lines = LinesData.category(npc.advanced, type);
        entries.clear();
        for (int slot : LinesData.slots(lines.lines)) entries.add(ArrayLine.of(lines.lines.get(slot)).copy());
        selected = entries.isEmpty() ? -1 : Math.max(0, Math.min(selected, entries.size() - 1));
    }
    @Override public void init() {
        super.init();
        addLabel(new GuiLabel(0, "cnpcplus.arrays.texts", guiLeft + 4, guiTop + 5));
        scroll = new GuiCustomScrollNop(this, 0);
        scroll.disabledSearch();
        scroll.setSize(158, 140);
        scroll.guiLeft = guiLeft + 4;
        scroll.guiTop = guiTop + 18;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) labels.add(label(i));
        scroll.setUnsortedList(labels);
        scroll.setSelectedIndex(selected);
        addScroll(scroll);
        text = new StyledTextArea(0, guiLeft + 170, guiTop + 66, 242, 60,
                selected < 0 ? new top.cnpcplus.text.StyledText("", "") : entries.get(selected).styled(),
                () -> selected < 0 ? new top.cnpcplus.text.StyledText("", "") : entries.get(selected).styled());
        text.enabled = selected >= 0;
        text.setListener(value -> {
            if (selected >= 0 && selected < entries.size()) {
                edited |= !entries.get(selected).getText().equals(value);
                entries.get(selected).setText(value);
                refreshLabel();
            }
        });
        add(text);
        toolbar = new NativeTextToolbar(this, text, guiTop + 18, () -> entries.get(selected).styled(), value -> {
            entries.get(selected).styled(value);
            edited = true;
            refreshLabel();
        }, false);
        addButton(new GuiButtonNop(this, 4, guiLeft + 170, guiTop + 133, 242, 20, "cnpcplus.arrays.sounds"));
        getButton(4).setEnabled(selected >= 0);
        addButton(new GuiButtonNop(this, 0, guiLeft + 4, guiTop + 166, 96, 20, "gui.add"));
        addButton(new GuiButtonNop(this, 1, guiLeft + 108, guiTop + 166, 96, 20, "gui.remove"));
        addButton(new GuiButtonNop(this, 2, guiLeft + 212, guiTop + 166, 96, 20, "cnpcplus.arrays.up"));
        addButton(new GuiButtonNop(this, 3, guiLeft + 316, guiTop + 166, 96, 20, "cnpcplus.arrays.down"));
        getButton(1).setEnabled(selected >= 0);
        getButton(2).setEnabled(selected > 0);
        getButton(3).setEnabled(selected >= 0 && selected + 1 < entries.size());
    }
    private void storeField() {
        if (text != null && selected >= 0 && selected < entries.size()) {
            edited |= !entries.get(selected).getText().equals(text.text);
            entries.get(selected).setText(text.text);
        }
    }
    private String label(int row) { return (row + 1) + ". " + entries.get(row).getText().replace('\n', ' '); }
    private void refreshLabel() {
        if (scroll != null && selected >= 0 && selected < scroll.getList().size()) scroll.getList().set(selected, label(selected));
    }
    @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) {
        super.render(graphics, x, y, partial);
        if (!hasSubGui() && toolbar != null) toolbar.draw(graphics);
    }
    @Override public void buttonEvent(GuiButtonNop button) {
        if (toolbar != null && toolbar.click(button.id)) return;
        storeField();
        switch (button.id) {
            case 0 -> { entries.add(new ArrayLine()); selected = entries.size() - 1; }
            case 1 -> { entries.remove(selected); selected = Math.min(selected, entries.size() - 1); }
            case 2, 3 -> {
                int next = selected + (button.id == 2 ? -1 : 1);
                if (selected < 0 || next < 0 || next >= entries.size()) return;
                Collections.swap(entries, selected, next);
                selected = next;
            }
            case 4 -> {
                edited = true;
                ArrayLine line = entries.get(selected);
                setSubGui(new SubGuiSoundArray(line.sounds, false, value -> { line.sounds = value; edited = true; }));
                return;
            }
        }
        edited = true;
        init();
    }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) { storeField(); selected = scroll.getSelectedIndex(); init(); }
    @Override public void scrollDoubleClicked(String label, GuiCustomScrollNop scroll) {}
    @Override public void setGuiData(CompoundTag tag) { storeField(); npc.advanced.readToNBT(tag); if (!edited) load(); init(); }
    @Override public void save() {
        storeField();
        HashMap<Integer, Line> lines = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) lines.put(i, entries.get(i).copy());
        LinesData.category(npc.advanced, type).lines = lines;
        Packets.sendServer(new SPacketMenuSave(EnumMenuType.ADVANCED, npc.advanced.save(new CompoundTag())));
    }
}
