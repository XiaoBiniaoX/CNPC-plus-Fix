package top.cnpcplus.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.shared.client.gui.components.*;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import top.cnpcplus.mixin.GuiButtonOptionsAccess;
import top.cnpcplus.wand.WandTools;

/** Read the actual extended button; selection uses its existing ADVANCED save handler. */
public final class SubGuiRoleJobSelect extends GuiBasic implements ICustomScrollListener {
    private final GuiNpcAdvanced parent;
    private final int buttonId;
    private final String[] options;
    private final List<Integer> visible = new ArrayList<>();
    private String search = "";
    private GuiCustomScrollNop list;

    public SubGuiRoleJobSelect(GuiNpcAdvanced parent, int buttonId) {
        this.parent = parent; this.buttonId = buttonId;
        options = ((GuiButtonOptionsAccess)parent.getButton(buttonId)).cnpcplus$options().clone();
        imageWidth = 280; imageHeight = 220; setBackground("menubg.png");
    }
    @Override public void init() {
        super.init();
        addTextField(new GuiTextFieldNop(0, this, guiLeft + 12, guiTop + 28, 256, 20, search));
        addWidget(getTextField(0));
        getTextField(0).setResponder(value -> { search = value; filter(); });
        list = new GuiCustomScrollNop(this, 0); list.disabledSearch();
        list.setSize(256, 136); list.guiLeft = guiLeft + 12; list.guiTop = guiTop + 52; addScroll(list);
        addButton(new GuiButtonNop(this, 0, guiLeft + 12, guiTop + 194, 122, 20, "gui.select"));
        addButton(new GuiButtonNop(this, 1, guiLeft + 146, guiTop + 194, 122, 20, "gui.cancel"));
        filter();
    }
    private void filter() {
        visible.clear();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < options.length; i++) {
            String label = I18n.get(options[i]);
            if (WandTools.matches(search, label) || WandTools.matches(search, options[i])) {
                visible.add(i); labels.add(i + "  " + label);
            }
        }
        list.setUnsortedList(labels);
        int selected = visible.indexOf(parent.getButton(buttonId).getValue());
        list.setSelectedIndex(selected); getButton(0).setEnabled(selected >= 0);
    }
    private void select() {
        int index = list.getSelectedIndex();
        if (index < 0 || index >= visible.size() || parent.npc == null) return;
        GuiButtonNop button = parent.getButton(buttonId);
        int value = visible.get(index);
        if (value != button.getValue()) { button.setDisplay(value); parent.buttonEvent(button); }
        close();
    }
    @Override public void buttonEvent(GuiButtonNop button) { if (button.id == 0) select(); else close(); }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) { getButton(0).setEnabled(scroll.getSelectedIndex() >= 0); }
    @Override public void scrollDoubleClicked(String selected, GuiCustomScrollNop scroll) { select(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (!hasSubGui()) { setFocused(getTextField(0).isFocused() ? getTextField(0) : null); setDragging(button == 0 && getTextField(0).isFocused()); }
        return handled;
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawString(font, I18n.get(buttonId == 8 ? "cnpcplus.select.roles" : "cnpcplus.select.jobs")
                + "  " + visible.size() + "/" + options.length, guiLeft + 12, guiTop + 12, 0x202020, false);
    }
}
