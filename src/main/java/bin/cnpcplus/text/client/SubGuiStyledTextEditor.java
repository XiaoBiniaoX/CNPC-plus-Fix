package bin.cnpcplus.text.client;

import bin.cnpcplus.text.StyledText;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.SubGuiNpcTextArea;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;

/** Same native text subpage and close callback, with a selection-only format toolbar. */
public final class SubGuiStyledTextEditor extends SubGuiNpcTextArea {
    private StyledText draft;
    private final StyledText original;
    private final Consumer<StyledText> commit;
    private StyledTextArea area;
    private final Function<StyledText, GuiScreen> preview;
    private final ResourceLocation panel = getResource("bgfilled.png");
    private static final int[] COLORS = {0xFFFFFF, 0xAAAAAA, 0x555555, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF};
    private String chosenColor = "FFFFFF";
    public SubGuiStyledTextEditor(StyledText value, Consumer<StyledText> commit) {
        this(value, commit, null);
    }
    public SubGuiStyledTextEditor(StyledText value, Consumer<StyledText> commit, Function<StyledText, GuiScreen> preview) {
        super(value.text); background = null; draft = original = value; this.commit = commit; this.preview = preview;
    }
    @Override public void initGui() {
        if (area != null) { draft = area.value(); text = draft.text; chosenColor = getTextField(220).getText(); }
        xSize = (int) (width * .88); ySize = (int) (xSize * .56);
        if (ySize > height * .95) { ySize = (int) (height * .95); xSize = (int) (ySize / .56); }
        super.initGui();
    }
    public void buildEditor() {
        int offset = (int) (ySize * .02), x = guiLeft + 1 + offset, y = guiTop + offset + 48;
        int editorWidth = xSize - 100 - offset, editorHeight = ySize - offset * 2 - 48;
        if (area == null) area = new StyledTextArea(2, x, y, editorWidth, editorHeight, draft);
        else area.resize(x, y, editorWidth, editorHeight);
        area.setListener(this); add(area);
        String[] actions = {"gui.clear", "gui.paste", "gui.copy", "remote.reset", "gui.close"};
        int[] ids = {102, 101, 100, 103, 0};
        for (int i = 0; i < ids.length; i++) addButton(new GuiNpcButton(ids[i], guiLeft + xSize - 90 - offset,
                guiTop + (i == 4 ? Math.min(160, ySize - 24) : 20 + i * 23), 56, 20, actions[i]));
        buildToolbar();
    }
    private void buildToolbar() {
        int x = area.x, y = area.y - 48, count = preview == null ? 5 : 6, w = Math.max(22, area.width / count);
        String[] labels = {"B", "U", "I", "cnpcplus.text.clearColor", "cnpcplus.text.clearStyle", "cnpcplus.text.preview"};
        for (int i = 0; i < count; i++) addButton(new GuiNpcButton(200 + i, x + i * w, y, w - 2, 20, labels[i]));
        int sw = Math.max(8, Math.min(14, (area.width - 100) / COLORS.length)), right = x + COLORS.length * sw + 4;
        addTextField(new GuiNpcTextField(220, this, right, y + 24, 50, 20, chosenColor));
        getTextField(220).setMaxStringLength(7);
        addButton(new GuiNpcButton(221, right + 52, y + 24, 40, 20, "gui.color"));
        for (int i = 0; i < COLORS.length; i++) addButton(new GuiNpcButton(230 + i, x + i * sw, y + 24, sw - 1, 20, ""));
    }
    @Override public void buttonEvent(GuiButton button) {
        if (button.id == 205 && preview != null) {
            GuiScreen screen = preview.apply(area.value());
            if (screen != null) mc.displayGuiScreen(screen);
            return;
        }
        if (button.id >= 200 && button.id <= 204) {
            area.format(button.id == 203 ? -2 : -1, button.id < 203 ? 1 << (button.id - 200) : 0, button.id == 204);
            return;
        }
        if (button.id == 221 || button.id >= 230 && button.id < 230 + COLORS.length) {
            String hex = button.id == 221 ? getTextField(220).getText().replace("#", "") : String.format("%06X", COLORS[button.id - 230]);
            if (!hex.matches("[0-9a-fA-F]{6}")) return;
            chosenColor = hex.toUpperCase(java.util.Locale.ROOT); getTextField(220).setText(chosenColor);
            area.format(Integer.parseInt(hex, 16), 0, false);
            return;
        }
        if (button.id == 103) { area.reset(original); return; }
        if (button.id == 102) { area.reset(new StyledText("", "")); return; }
        if (button.id == 101) { area.reset(area.value().edit(noppes.npcs.NoppesStringUtils.getClipboardContents())); return; }
        if (button.id == 100) noppes.npcs.NoppesStringUtils.setClipboardContents(area.text);
        if (button.id == 0) close();
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        // Slice the native 420x256 panel first, then fit it once to the actual editor bounds.
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(guiLeft, guiTop, 0);
        GlStateManager.scale(xSize / 420F, ySize / 256F, 1);
        mc.getTextureManager().bindTexture(panel);
        drawTexturedModalRect(0, 0, 0, 0, 250, 256);
        drawTexturedModalRect(250, 0, 86, 0, 170, 256);
        GlStateManager.popMatrix();
        super.drawScreen(mx, my, partial);
        if (hasSubGui()) return;
        for (int i = 0; i < COLORS.length; i++) {
            GuiButton b = getButton(230 + i);
            drawRect(b.x + 2, b.y + 3, b.x + b.width - 2, b.y + 17, 0xFF000000 | COLORS[i]);
        }
    }
    @Override public void save() { draft = area.value(); text = draft.text; commit.accept(draft); }
}
