package bin.cnpcplus.client.gui;

import bin.cnpcplus.mixin.advanced.IGuiButtonOptions;
import bin.cnpcplus.transport.client.Pinyin;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import noppes.npcs.client.gui.util.*;

/** The actual extended native options, preserving indices and the original save handler. */
public final class SubGuiRoleJobSelect extends SubGuiInterface implements ICustomScrollListener {
    private final String[] options;
    private final int selected;
    private final boolean role;
    private final IntConsumer accept;
    private final List<Integer> visible = new ArrayList<>();
    private String search = "";
    private GuiCustomScroll list;
    public SubGuiRoleJobSelect(GuiNpcButton button, boolean role, IntConsumer accept) {
        options = ((IGuiButtonOptions) button).cnpcplus$options().clone(); selected = button.getValue();
        this.role = role; this.accept = accept; ySize = 220; closeOnEsc = true; setBackground("menubg.png");
    }
    @Override public void initGui() {
        xSize = Math.min(224, width - 12);
        super.initGui();
        int contentWidth = xSize - 24, buttonWidth = (contentWidth - 6) / 2;
        addTextField(new DragSearchField(0, this, guiLeft + 12, guiTop + 28, contentWidth, 18, search));
        list = new GuiCustomScroll(this, 0); list.setSize(contentWidth, 136); list.guiLeft = guiLeft + 12; list.guiTop = guiTop + 52; addScroll(list);
        addButton(new GuiNpcButton(0, guiLeft + 12, guiTop + 194, buttonWidth, 20, "gui.select"));
        addButton(new GuiNpcButton(1, guiLeft + xSize - 12 - buttonWidth, guiTop + 194, buttonWidth, 20, "gui.cancel")); filter();
    }
    private void filter() {
        visible.clear(); List<String> labels = new ArrayList<>();
        for (int i = 0; i < options.length; i++) {
            String label = I18n.format(options[i]);
            if (Pinyin.matches(search, label) || Pinyin.matches(search, options[i])) { visible.add(i); labels.add(i + "  " + label); }
        }
        list.setUnsortedList(labels); list.selected = visible.indexOf(selected); getButton(0).enabled = list.selected >= 0;
    }
    private void select() {
        if (list.selected < 0 || list.selected >= visible.size()) return;
        int selected = visible.get(list.selected); close(); accept.accept(selected);
    }
    @Override public void buttonEvent(GuiButton button) { if (button.id == 0) select(); else close(); }
    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll scroll) { getButton(0).enabled = scroll.selected >= 0; }
    @Override public void scrollDoubleClicked(String label, GuiCustomScroll scroll) { select(); }
    @Override public void keyTyped(char character, int key) throws java.io.IOException {
        super.keyTyped(character, key); String value = getTextField(0).getText();
        if (!value.equals(search)) { search = value; filter(); }
    }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        fontRenderer.drawString(I18n.format(role ? "cnpcplus.select.roles" : "cnpcplus.select.jobs") + "  " + visible.size() + "/" + options.length,
                guiLeft + 12, guiTop + 12, 0x202020);
    }
}
