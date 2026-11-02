package top.cnpcplus.script.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import top.cnpcplus.client.gui.NativeSearchField;
import top.cnpcplus.script.ScriptApiDocs;

/** Native reference browser: category, signatures and provenance; it never edits the parent script. */
public final class SubGuiApiDocs extends GuiBasic implements ICustomScrollListener {
    private final List<ScriptApiDocs.Line> all = ScriptApiDocs.lines();
    private final Map<String, List<ScriptApiDocs.Line>> filtered = new TreeMap<>();
    private List<ScriptApiDocs.Line> matches = List.of();
    private List<ScriptApiDocs.Line> visible = List.of();
    private String search = "", category = "";
    private GuiCustomScrollNop groups, signatures;
    private int categoryWidth, count, detailY, detailHeight, detailScroll;
    private List<FormattedCharSequence> detail = List.of();

    public SubGuiApiDocs() { drawDefaultBackground = false; }

    @Override public void m_7856_() {
        imageWidth = Math.min(760, width - 16);
        imageHeight = Math.min(440, height - 16);
        categoryWidth = Math.max(86, imageWidth / 4);
        super.m_7856_();
        addTextField(new NativeSearchField(0, this, guiLeft + 10, guiTop + 25, imageWidth - 20, search));
        addWidget(getTextField(0)); // Event-only registration: GuiBasic draws the native field exactly once.
        getTextField(0).setResponder(value -> { search = value; category = ""; filter(); });
        detailHeight = Math.min(110, Math.max(64, imageHeight / 3));
        int listHeight = imageHeight - detailHeight - 102;
        groups = scroll(0, guiLeft + 10, categoryWidth, listHeight);
        signatures = scroll(1, guiLeft + categoryWidth + 16, imageWidth - categoryWidth - 26, listHeight);
        detailY = guiTop + 66 + listHeight;
        addButton(new GuiButtonNop(this, 0, guiLeft + imageWidth - 110, guiTop + imageHeight - 26, 100, 20, "gui.done"));
        filter();
        setFocused(getTextField(0));
    }

    private GuiCustomScrollNop scroll(int id, int x, int w, int h) {
        GuiCustomScrollNop list = new GuiCustomScrollNop(this, id);
        list.disabledSearch();
        list.setSize(w, h);
        list.guiLeft = x;
        list.guiTop = guiTop + 60;
        addScroll(list);
        return list;
    }

    private void filter() {
        filtered.clear();
        List<ScriptApiDocs.Line> found = new ArrayList<>();
        String[] words = search.strip().toLowerCase(Locale.ROOT).split("\\s+");
        for (ScriptApiDocs.Line line : all) {
            String text = (line.group() + " " + line.owner() + " " + line.signature() + " " + line.source()).toLowerCase(Locale.ROOT);
            if (!java.util.Arrays.stream(words).allMatch(text::contains)) continue;
            filtered.computeIfAbsent(line.group(), ignored -> new ArrayList<>()).add(line);
            found.add(line);
        }
        matches = found;
        count = matches.size();
        List<String> labels = new ArrayList<>();
        labels.add(I18n.get("cnpcplus.api.all"));
        labels.addAll(filtered.keySet());
        groups.setUnsortedList(labels);
        if (!filtered.containsKey(category)) category = "";
        groups.setSelectedIndex(category.isEmpty() ? 0 : labels.indexOf(category));
        showCategory();
    }

    private void showCategory() {
        visible = category.isEmpty() ? matches : filtered.getOrDefault(category, List.of());
        signatures.setUnsortedList(visible.stream().map(line ->
                line.signature() + "  [" + line.owner().replace("noppes.npcs.api.", "").replace('$', '.') + "]").toList());
        signatures.setSelectedIndex(visible.isEmpty() ? -1 : 0);
        showDetail();
    }

    private void showDetail() {
        detailScroll = 0;
        int index = signatures.getSelectedIndex();
        if (index < 0 || index >= visible.size()) {
            detail = font.split(Component.translatable("cnpcplus.api.no_results"), imageWidth - 36);
            return;
        }
        ScriptApiDocs.Line line = visible.get(index);
        String source = ScriptApiDocs.UNKNOWN.equals(line.source()) ? I18n.get("cnpcplus.api.unknown") : line.source();
        detail = font.split(Component.literal(line.signature() + "\n" + line.owner().replace('$', '.')
                + "\n" + line.group() + "\n" + I18n.get("cnpcplus.api.source") + ": " + source), imageWidth - 36);
    }

    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop list) {
        if (list == groups) { category = groups.getSelectedIndex() == 0 ? "" : groups.getSelected(); showCategory(); }
        else if (list == signatures) showDetail();
    }
    @Override public void scrollDoubleClicked(String selected, GuiCustomScrollNop list) { /* Reference only. */ }
    @Override public void buttonEvent(GuiButtonNop button) { close(); }
    @Override public boolean m_5534_(char character, int modifiers) {
        return getTextField(0).m_5534_(character, modifiers);
    }
    @Override public boolean m_7933_(int key, int scan, int modifiers) {
        if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && getTextField(0).isFocused())
            return getTextField(0).keyPressed(key, scan, modifiers);
        return super.m_7933_(key, scan, modifiers);
    }
    @Override public boolean m_6050_(double x, double y, double delta) {
        if (x >= guiLeft + 10 && x < guiLeft + imageWidth - 10 && y >= detailY && y < detailY + detailHeight) {
            int rows = (detailHeight - 8) / font.lineHeight;
            detailScroll = Math.max(0, Math.min(Math.max(0, detail.size() - rows), detailScroll - (int)Math.signum(delta) * 3));
            return true;
        }
        return super.m_6050_(x, y, delta);
    }
    @Override public boolean m_6375_(double x, double y, int button) {
        boolean handled = super.m_6375_(x, y, button);
        if (getTextField(0).isFocused()) setFocused(getTextField(0));
        setDragging(button == 0 && getTextField(0).isFocused());
        return handled;
    }

    @Override public void m_88315_(GuiGraphics g, int x, int y, float partial) {
        g.fill(0, 0, width, height, 0xB0101010);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth, guiTop + imageHeight, 0xFF303030);
        g.fill(guiLeft + 1, guiTop + 1, guiLeft + imageWidth - 1, guiTop + imageHeight - 1, 0xFFC6C6C6);
        g.fill(guiLeft + 10, detailY, guiLeft + imageWidth - 10, detailY + detailHeight, 0xFF202020);
        super.m_88315_(g, x, y, partial);
        g.drawString(font, I18n.get("cnpcplus.api.title"), guiLeft + 10, guiTop + 10, 0x202020, false);
        g.drawString(font, I18n.get("cnpcplus.api.category"), guiLeft + 10, guiTop + 49, 0x202020, false);
        g.drawString(font, I18n.get("cnpcplus.api.signature"), guiLeft + categoryWidth + 16, guiTop + 49, 0x202020, false);
        g.drawString(font, visible.size() + " / " + count + " / " + all.size(), guiLeft + 10, guiTop + imageHeight - 20, 0x202020, false);
        int rows = (detailHeight - 8) / font.lineHeight;
        for (int row = 0; row < rows && detailScroll + row < detail.size(); row++) {
            g.drawString(font, detail.get(detailScroll + row), guiLeft + 14, detailY + 4 + row * font.lineHeight, 0xFFF0F0F0, false);
        }
        if (detail.size() > rows) {
            int track = detailHeight - 8, thumb = Math.max(8, track * rows / detail.size());
            int yy = detailY + 4 + (track - thumb) * detailScroll / (detail.size() - rows);
            g.fill(guiLeft + imageWidth - 15, detailY + 4, guiLeft + imageWidth - 12, detailY + detailHeight - 4, 0xFF555555);
            g.fill(guiLeft + imageWidth - 15, yy, guiLeft + imageWidth - 12, yy + thumb, 0xFFD0D0D0);
        }
    }
}
