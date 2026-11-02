package top.cnpcplus.lines.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import top.cnpcplus.lines.SoundArray;

/** Native list editor for per-line and per-event sounds. */
public final class SubGuiSoundArray extends GuiBasic implements ICustomScrollListener {
    private final SoundArray draft;
    private final Consumer<SoundArray> commit;
    private final boolean editPitch;
    private int selected = -1;
    private GuiCustomScrollNop scroll;
    public SubGuiSoundArray(SoundArray sounds, boolean editPitch, Consumer<SoundArray> commit) {
        draft = sounds.copy();
        this.commit = commit;
        this.editPitch = editPitch;
        imageWidth = 366;
        imageHeight = 200;
        title = "";
        setBackground("menubg.png");
        if (!draft.sounds.isEmpty()) selected = 0;
    }
    @Override public void init() {
        super.init();
        addLabel(new GuiLabel(0, "cnpcplus.arrays.sounds", guiLeft + 4, guiTop + 5));
        scroll = new GuiCustomScrollNop(this, 0);
        scroll.disabledSearch();
        scroll.setSize(358, 112);
        scroll.guiLeft = guiLeft + 4;
        scroll.guiTop = guiTop + 18;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < draft.sounds.size(); i++) labels.add((i + 1) + ". " + draft.sounds.get(i));
        scroll.setUnsortedList(labels);
        scroll.setSelectedIndex(selected);
        addScroll(scroll);
        addTextField(new GuiTextFieldNop(0, this, guiLeft + 4, guiTop + 135, 275, 20, selected < 0 ? "" : draft.sounds.get(selected)));
        getTextField(0).enabled = selected >= 0;
        getTextField(0).setResponder(value -> {
            if (selected >= 0 && selected < draft.sounds.size()) draft.sounds.set(selected, value);
        });
        addButton(new GuiButtonNop(this, 0, guiLeft + 283, guiTop + 135, 79, 20, "mco.template.button.select"));
        getButton(0).setEnabled(selected >= 0);
        addButton(new GuiButtonNop(this, 1, guiLeft + 4, guiTop + 161, 70, 20, "gui.add"));
        addButton(new GuiButtonNop(this, 2, guiLeft + 78, guiTop + 161, 70, 20, "gui.remove"));
        addButton(new GuiButtonNop(this, 3, guiLeft + 152, guiTop + 161, 35, 20, "↑"));
        addButton(new GuiButtonNop(this, 4, guiLeft + 191, guiTop + 161, 35, 20, "↓"));
        getButton(2).setEnabled(selected >= 0);
        getButton(3).setEnabled(selected > 0);
        getButton(4).setEnabled(selected >= 0 && selected + 1 < draft.sounds.size());
        if (editPitch) {
            addLabel(new GuiLabel(1, "cnpcplus.arrays.pitch", guiLeft + 232, guiTop + 166));
            addButton(new GuiButtonYesNo(this, 5, guiLeft + 310, guiTop + 161, 52, 20, draft.randomPitch));
        }
    }
    private void storeField() {
        if (selected >= 0 && selected < draft.sounds.size() && getTextField(0) != null) draft.sounds.set(selected, getTextField(0).getValue());
    }
    @Override public void buttonEvent(GuiButtonNop button) {
        storeField();
        switch (button.id) {
            case 0 -> { setSubGui(new GuiSoundSelection(draft.sounds.get(selected))); return; }
            case 1 -> { draft.sounds.add(""); selected = draft.sounds.size() - 1; }
            case 2 -> { draft.sounds.remove(selected); selected = Math.min(selected, draft.sounds.size() - 1); }
            case 3, 4 -> {
                int next = selected + (button.id == 3 ? -1 : 1);
                if (selected < 0 || next < 0 || next >= draft.sounds.size()) return;
                Collections.swap(draft.sounds, selected, next);
                selected = next;
            }
            case 5 -> draft.randomPitch = button.getValue() == 1;
        }
        init();
    }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) { storeField(); selected = scroll.getSelectedIndex(); init(); }
    @Override public void scrollDoubleClicked(String label, GuiCustomScrollNop scroll) {}
    @Override public void subGuiClosed(Screen subgui) {
        if (subgui instanceof GuiSoundSelection sound && sound.selectedResource != null && selected >= 0) {
            draft.sounds.set(selected, sound.selectedResource.toString());
            getTextField(0).setValue(sound.selectedResource.toString());
        }
    }
    @Override public void save() { storeField(); commit.accept(draft.copy()); }
}
