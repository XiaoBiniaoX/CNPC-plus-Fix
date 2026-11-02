package bin.cnpcplus.script.client;

import bin.cnpcplus.client.gui.DragSearchField;
import bin.cnpcplus.script.ScriptApiDocs;
import java.util.*;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import noppes.npcs.client.gui.util.*;
import org.lwjgl.input.Mouse;

/** Reference browser matching the sibling layout; never edits or runs the parent script. */
public final class SubGuiApiDocs extends SubGuiInterface implements ICustomScrollListener {
    private final List<ScriptApiDocs.Line> all = ScriptApiDocs.lines();
    private final Map<String, List<ScriptApiDocs.Line>> filtered = new TreeMap<>();
    private List<ScriptApiDocs.Line> matches = Collections.emptyList(), visible = Collections.emptyList();
    private List<String> detail = Collections.emptyList();
    private String search = "", category = "";
    private GuiCustomScroll groups, signatures;
    private int categoryWidth, count, detailY, detailHeight, detailScroll;
    public SubGuiApiDocs(GuiScreen editor) { parent = editor; drawDefaultBackground = false; closeOnEsc = true; }
    @Override public void initGui() {
        xSize = Math.min(760, width - 16); ySize = Math.min(440, height - 16);
        categoryWidth = Math.max(86, xSize / 4);
        super.initGui();
        addTextField(new DragSearchField(0, this, guiLeft + 10, guiTop + 25, xSize - 20, 20, search));
        getTextField(0).setMaxStringLength(32767); getTextField(0).setFocused(true);
        detailHeight = Math.min(110, Math.max(64, ySize / 3));
        int listHeight = ySize - detailHeight - 102;
        groups = new GuiCustomScroll(this, 0); groups.setSize(categoryWidth, listHeight);
        groups.guiLeft = guiLeft + 10; groups.guiTop = guiTop + 60; addScroll(groups);
        signatures = new GuiCustomScroll(this, 1); signatures.setSize(xSize - categoryWidth - 26, listHeight);
        signatures.guiLeft = guiLeft + categoryWidth + 16; signatures.guiTop = guiTop + 60; addScroll(signatures);
        detailY = guiTop + 66 + listHeight;
        addButton(new GuiNpcButton(0, guiLeft + xSize - 110, guiTop + ySize - 26, 100, 20, "gui.done"));
        filter();
    }
    private void filter() {
        filtered.clear(); List<ScriptApiDocs.Line> found = new ArrayList<>();
        String[] words = search.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (ScriptApiDocs.Line line : all) {
            String text = (line.group + " " + line.owner + " " + line.signature + " " + line.source).toLowerCase(Locale.ROOT);
            if (!Arrays.stream(words).allMatch(text::contains)) continue;
            filtered.computeIfAbsent(line.group, ignored -> new ArrayList<>()).add(line); found.add(line);
        }
        matches = found; count = matches.size();
        List<String> labels = new ArrayList<>(); labels.add(I18n.format("cnpcplus.api.all")); labels.addAll(filtered.keySet());
        groups.setUnsortedList(labels);
        if (!filtered.containsKey(category)) category = "";
        groups.selected = category.isEmpty() ? 0 : labels.indexOf(category); showCategory();
    }
    private void showCategory() {
        visible = category.isEmpty() ? matches : filtered.getOrDefault(category, Collections.emptyList());
        List<String> labels = new ArrayList<>();
        for (ScriptApiDocs.Line line : visible) labels.add(line.signature + "  [" + line.owner.replace("noppes.npcs.api.", "").replace('$', '.') + "]");
        signatures.setUnsortedList(labels); signatures.selected = visible.isEmpty() ? -1 : 0; showDetail();
    }
    private void showDetail() {
        detailScroll = 0; int index = signatures.selected;
        if (index < 0 || index >= visible.size()) { detail = fontRenderer.listFormattedStringToWidth(I18n.format("cnpcplus.api.no_results"), xSize - 36); return; }
        ScriptApiDocs.Line line = visible.get(index);
        String source = ScriptApiDocs.UNKNOWN.equals(line.source) ? I18n.format("cnpcplus.api.unknown") : line.source;
        detail = fontRenderer.listFormattedStringToWidth(line.signature + "\n" + line.owner.replace('$', '.') + "\n" + line.group
                + "\n" + I18n.format("cnpcplus.api.source") + ": " + source, xSize - 36);
    }
    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll list) {
        if (list == groups) { category = groups.selected == 0 ? "" : groups.getSelected(); showCategory(); }
        else if (list == signatures) showDetail();
    }
    @Override public void scrollDoubleClicked(String selected, GuiCustomScroll list) { }
    @Override public void buttonEvent(GuiButton button) { close(); }
    @Override public void close() { mc.displayGuiScreen(parent); }
    @Override public void keyTyped(char c, int key) throws IOException {
        super.func_73869_a(c, key);
        String value = getTextField(0).getText();
        if (!search.equals(value)) { search = value; category = ""; filter(); }
    }
    @Override public void drawScreen(int x, int y, float partial) {
        int wheel = 0;
        if (x >= guiLeft + 10 && x < guiLeft + xSize - 10 && y >= detailY && y < detailY + detailHeight) wheel = Mouse.getDWheel();
        int rows = (detailHeight - 8) / fontRenderer.FONT_HEIGHT;
        if (wheel != 0) detailScroll = Math.max(0, Math.min(Math.max(0, detail.size() - rows), detailScroll - Integer.signum(wheel) * 3));
        drawRect(0, 0, width, height, 0xB0101010);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF303030);
        drawRect(guiLeft + 1, guiTop + 1, guiLeft + xSize - 1, guiTop + ySize - 1, 0xFFC6C6C6);
        drawRect(guiLeft + 10, detailY, guiLeft + xSize - 10, detailY + detailHeight, 0xFF202020);
        super.drawScreen(x, y, partial);
        fontRenderer.drawString(I18n.format("cnpcplus.api.title"), guiLeft + 10, guiTop + 10, 0x202020);
        fontRenderer.drawString(I18n.format("cnpcplus.api.category"), guiLeft + 10, guiTop + 49, 0x202020);
        fontRenderer.drawString(I18n.format("cnpcplus.api.signature"), guiLeft + categoryWidth + 16, guiTop + 49, 0x202020);
        fontRenderer.drawString(visible.size() + " / " + count + " / " + all.size(), guiLeft + 10, guiTop + ySize - 20, 0x202020);
        for (int row = 0; row < rows && detailScroll + row < detail.size(); row++)
            fontRenderer.drawString(detail.get(detailScroll + row), guiLeft + 14, detailY + 4 + row * fontRenderer.FONT_HEIGHT, 0xF0F0F0);
        if (detail.size() > rows) {
            int track = detailHeight - 8, thumb = Math.max(8, track * rows / detail.size());
            int yy = detailY + 4 + (track - thumb) * detailScroll / (detail.size() - rows);
            drawRect(guiLeft + xSize - 15, detailY + 4, guiLeft + xSize - 12, detailY + detailHeight - 4, 0xFF555555);
            drawRect(guiLeft + xSize - 15, yy, guiLeft + xSize - 12, yy + thumb, 0xFFD0D0D0);
        }
    }
}
