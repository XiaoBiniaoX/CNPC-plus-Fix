package bin.cnpcplus.lines.client;

import bin.cnpcplus.lines.ArrayLine;
import bin.cnpcplus.lines.LinesData;
import bin.cnpcplus.text.StyledText;
import bin.cnpcplus.text.client.StyledTextArea;
import bin.cnpcplus.text.client.SubGuiStyledTextEditor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.EntityNPCInterface;

public final class GuiLinesArrayEditor extends GuiNPCInterface2 implements ICustomScrollListener, IGuiData, ITextChangeListener, ITextfieldListener, ISubGuiListener {
    private final int type;
    private final List<ArrayLine> entries = new ArrayList<>();
    private int selected = -1, selectedSound = -1;
    private StyledTextArea text;
    private boolean edited;
    private GuiCustomScroll scroll, sounds;

    public GuiLinesArrayEditor(EntityNPCInterface npc, int type) {
        super(npc); this.type = type; load();
        Client.sendData(EnumPacketServer.MainmenuAdvancedGet);
    }
    private void load() {
        Lines lines = LinesData.category(npc.advanced, type);
        entries.clear();
        for (int slot : LinesData.slots(lines.lines)) entries.add(ArrayLine.of(lines.lines.get(slot)).copy());
        selected = Math.min(selected, entries.size() - 1);
    }
    @Override public void initGui() {
        super.initGui();
        addLabel(new GuiNpcLabel(0, "cnpcplus.arrays.texts", guiLeft + 4, guiTop + 5));
        scroll = new GuiCustomScroll(this, 0);
        scroll.setSize(158, 104); scroll.guiLeft = guiLeft + 4; scroll.guiTop = guiTop + 18;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) labels.add(label(i));
        scroll.setUnsortedList(labels); scroll.selected = selected; addScroll(scroll);
        text = null; sounds = null;
        if (selected >= 0) {
            ArrayLine line = entries.get(selected);
            addLabel(new GuiNpcLabel(2, "cnpcplus.arrays.sounds", guiLeft + 170, guiTop + 5));
            selectedSound = Math.min(selectedSound, line.sounds.sounds.size() - 1);
            sounds = new GuiCustomScroll(this, 1);
            sounds.setSize(242, 54); sounds.guiLeft = guiLeft + 170; sounds.guiTop = guiTop + 18;
            List<String> soundLabels = new ArrayList<>();
            for (int i = 0; i < line.sounds.sounds.size(); i++) soundLabels.add((i + 1) + ". " + line.sounds.sounds.get(i));
            sounds.setUnsortedList(soundLabels); sounds.selected = selectedSound; addScroll(sounds);
            addTextField(new GuiNpcTextField(10, this, fontRenderer, guiLeft + 170, guiTop + 76, 158, 20,
                    selectedSound < 0 ? "" : line.sounds.sounds.get(selectedSound)));
            getTextField(10).setMaxStringLength(32767); getTextField(10).enabled = selectedSound >= 0;
            addButton(new GuiNpcButton(10, guiLeft + 332, guiTop + 76, 80, 20, "gui.selectSound"));
            addButton(new GuiNpcButton(11, guiLeft + 170, guiTop + 100, 70, 20, "gui.add"));
            addButton(new GuiNpcButton(12, guiLeft + 244, guiTop + 100, 70, 20, "gui.remove"));
            addButton(new GuiNpcButton(13, guiLeft + 318, guiTop + 100, 45, 20, "cnpcplus.arrays.up"));
            addButton(new GuiNpcButton(14, guiLeft + 367, guiTop + 100, 45, 20, "cnpcplus.arrays.down"));
            getButton(10).setEnabled(selectedSound >= 0); getButton(12).setEnabled(selectedSound >= 0);
            getButton(13).setEnabled(selectedSound > 0);
            getButton(14).setEnabled(selectedSound >= 0 && selectedSound + 1 < line.sounds.sounds.size());
            text = new StyledTextArea(0, guiLeft + 4, guiTop + 150, 408, 42, new StyledText(line.getText(), line.spans));
            text.setListener(this); add(text);
            addButton(new GuiNpcButton(5, guiLeft + 170, guiTop + 126, 242, 20, "cnpcplus.text.edit"));
        } else addLabel(new GuiNpcLabel(1, "cnpcplus.arrays.selectText", guiLeft + 170, guiTop + 24));
        addButton(new GuiNpcButton(0, guiLeft + 4, guiTop + 126, 38, 20, "gui.add"));
        addButton(new GuiNpcButton(1, guiLeft + 44, guiTop + 126, 38, 20, "gui.remove"));
        addButton(new GuiNpcButton(2, guiLeft + 84, guiTop + 126, 38, 20, "cnpcplus.arrays.up"));
        addButton(new GuiNpcButton(3, guiLeft + 124, guiTop + 126, 38, 20, "cnpcplus.arrays.down"));
        getButton(1).setEnabled(selected >= 0); getButton(2).setEnabled(selected > 0);
        getButton(3).setEnabled(selected >= 0 && selected + 1 < entries.size());
    }
    private void storeField() {
        GuiNpcTextField.unfocus();
        if (text != null && selected >= 0) { textUpdate(text.text); entries.get(selected).spans = text.value().spans; }
    }
    private String label(int row) { return (row + 1) + ". " + entries.get(row).getText().replace('\n', ' '); }
    @Override public void textUpdate(String value) {
        ArrayLine line = entries.get(selected);
        edited |= !line.getText().equals(value); line.setText(value);
        if (scroll != null) scroll.getList().set(selected, label(selected));
    }
    @Override public void buttonEvent(GuiButton button) {
        storeField();
        switch (button.id) {
            case 0: entries.add(new ArrayLine()); selected = entries.size() - 1; selectedSound = -1; break;
            case 1: entries.remove(selected); selected = Math.min(selected, entries.size() - 1); selectedSound = -1; break;
            case 2: case 3:
                int next = selected + (button.id == 2 ? -1 : 1);
                Collections.swap(entries, selected, next); selected = next; break;
            case 10: setSubGui(new GuiSoundSelection(entries.get(selected).sounds.sounds.get(selectedSound))); return;
            case 11: entries.get(selected).sounds.sounds.add(""); selectedSound = entries.get(selected).sounds.sounds.size() - 1; break;
            case 12: entries.get(selected).sounds.sounds.remove(selectedSound); selectedSound = Math.min(selectedSound, entries.get(selected).sounds.sounds.size() - 1); break;
            case 13: case 14:
                int nextSound = selectedSound + (button.id == 13 ? -1 : 1);
                Collections.swap(entries.get(selected).sounds.sounds, selectedSound, nextSound); selectedSound = nextSound; break;
            case 5:
                ArrayLine editing = entries.get(selected); edited = true;
                setSubGui(new SubGuiStyledTextEditor(new StyledText(editing.getText(), editing.spans), value -> {
                    editing.setText(value.text); editing.spans = value.spans;
                    text.reset(value);
                })); return;
            default: return;
        }
        edited = true; initGui();
    }
    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll scroll) {
        storeField();
        if (scroll == sounds) selectedSound = scroll.selected;
        else { selected = scroll.selected; selectedSound = -1; }
        initGui();
    }
    @Override public void scrollDoubleClicked(String label, GuiCustomScroll scroll) { }
    @Override public void unFocused(GuiNpcTextField field) {
        if (field.getId() != 10 || selectedSound < 0) return;
        List<String> values = entries.get(selected).sounds.sounds;
        edited |= !values.get(selectedSound).equals(field.getText()); values.set(selectedSound, field.getText());
        if (sounds != null) sounds.getList().set(selectedSound, (selectedSound + 1) + ". " + field.getText());
    }
    @Override public void subGuiClosed(SubGuiInterface gui) {
        if (!(gui instanceof GuiSoundSelection)) return;
        GuiSoundSelection sound = (GuiSoundSelection) gui;
        if (sound.selectedResource == null) return;
        edited = true; entries.get(selected).sounds.sounds.set(selectedSound, sound.selectedResource.toString()); initGui();
    }
    @Override public void setGuiData(NBTTagCompound tag) {
        storeField(); npc.advanced.readToNBT(tag); if (!edited) load(); initGui();
    }
    @Override public void save() {
        storeField();
        HashMap<Integer, Line> lines = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) lines.put(i, entries.get(i).copy());
        LinesData.category(npc.advanced, type).lines = lines;
        Client.sendData(EnumPacketServer.MainmenuAdvancedSave, npc.advanced.writeToNBT(new NBTTagCompound()));
    }
}
