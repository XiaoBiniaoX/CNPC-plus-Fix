package bin.cnpcplus.lines.client;

import bin.cnpcplus.lines.SoundArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.client.gui.util.*;

/** Private draft shared by line sounds and the five NPC sound events. */
public final class SubGuiSoundArray extends SubGuiInterface implements ICustomScrollListener, ITextfieldListener, ISubGuiListener {
    private final SoundArray draft;
    private final Consumer<SoundArray> commit;
    private final boolean editPitch;
    private int selected = -1;
    private GuiCustomScroll scroll;

    public SubGuiSoundArray(SoundArray sounds, boolean editPitch, Consumer<SoundArray> commit) {
        this.draft = sounds.copy();
        this.commit = commit;
        this.editPitch = editPitch;
        xSize = 366; ySize = 214; closeOnEsc = true;
        setBackground("menubg.png");
        if (!draft.sounds.isEmpty()) selected = 0;
    }

    @Override public void initGui() {
        super.initGui();
        addLabel(new GuiNpcLabel(0, "cnpcplus.arrays.sounds", guiLeft + 4, guiTop + 5));
        scroll = new GuiCustomScroll(this, 0);
        scroll.setSize(358, 108);
        scroll.guiLeft = guiLeft + 4; scroll.guiTop = guiTop + 18;
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < draft.sounds.size(); i++) labels.add((i + 1) + ". " + draft.sounds.get(i));
        scroll.setUnsortedList(labels); scroll.selected = selected; addScroll(scroll);
        addTextField(new GuiNpcTextField(0, this, fontRenderer, guiLeft + 4, guiTop + 131, 275, 20,
                selected < 0 ? "" : draft.sounds.get(selected)));
        getTextField(0).enabled = selected >= 0;
        addButton(new GuiNpcButton(0, guiLeft + 283, guiTop + 131, 79, 20, "gui.selectSound"));
        getButton(0).setEnabled(selected >= 0);
        addButton(new GuiNpcButton(1, guiLeft + 4, guiTop + 157, 70, 20, "gui.add"));
        addButton(new GuiNpcButton(2, guiLeft + 78, guiTop + 157, 70, 20, "gui.remove"));
        addButton(new GuiNpcButton(3, guiLeft + 152, guiTop + 157, 35, 20, "cnpcplus.arrays.up"));
        addButton(new GuiNpcButton(4, guiLeft + 191, guiTop + 157, 35, 20, "cnpcplus.arrays.down"));
        getButton(2).setEnabled(selected >= 0);
        getButton(3).setEnabled(selected > 0);
        getButton(4).setEnabled(selected >= 0 && selected + 1 < draft.sounds.size());
        if (editPitch) {
            addLabel(new GuiNpcLabel(1, "cnpcplus.arrays.pitch", guiLeft + 232, guiTop + 162));
            addButton(new GuiNpcButtonYesNo(5, guiLeft + 310, guiTop + 157, 52, 20, draft.randomPitch));
        }
        addButton(new GuiNpcButton(6, guiLeft + 283, guiTop + 185, 79, 20, "gui.done"));
    }

    private void storeField() {
        GuiNpcTextField.unfocus();
        if (selected >= 0 && getTextField(0) != null) draft.sounds.set(selected, getTextField(0).getText());
    }

    @Override public void buttonEvent(GuiButton button) {
        storeField();
        switch (button.id) {
            case 0: setSubGui(new GuiSoundSelection(draft.sounds.get(selected))); return;
            case 1: draft.sounds.add(""); selected = draft.sounds.size() - 1; break;
            case 2: draft.sounds.remove(selected); selected = Math.min(selected, draft.sounds.size() - 1); break;
            case 3: case 4:
                int next = selected + (button.id == 3 ? -1 : 1);
                Collections.swap(draft.sounds, selected, next); selected = next; break;
            case 5: draft.randomPitch = ((GuiNpcButtonYesNo) button).getBoolean(); break;
            case 6: close(); return;
        }
        initGui();
    }
    @Override public void unFocused(GuiNpcTextField field) {
        if (selected >= 0) draft.sounds.set(selected, field.getText());
    }
    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll scroll) {
        storeField(); selected = scroll.selected; initGui();
    }
    @Override public void scrollDoubleClicked(String label, GuiCustomScroll scroll) { }
    @Override public void subGuiClosed(SubGuiInterface gui) {
        GuiSoundSelection sound = (GuiSoundSelection) gui;
        if (sound.selectedResource != null) {
            draft.sounds.set(selected, sound.selectedResource.toString());
            getTextField(0).setText(sound.selectedResource.toString());
        }
    }
    @Override public void save() { storeField(); commit.accept(draft.copy()); }
}
