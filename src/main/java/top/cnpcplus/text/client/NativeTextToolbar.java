package top.cnpcplus.text.client;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import top.cnpcplus.text.StyledText;
import top.cnpcplus.transport.TransportSpans;

/** Selection-only controls; never rebuild fields on a color click. */
public final class NativeTextToolbar {
    private static final int[] COLORS = {0xFFFFFF, 0xAAAAAA, 0x555555, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF};
    private final GuiBasic parent;
    private final GuiTextArea area;
    private final Supplier<StyledText> get;
    private final Consumer<StyledText> set;
    private int chosenColor = 0xFFFFFF;
    public NativeTextToolbar(GuiBasic parent, GuiTextArea area, int y, Supplier<StyledText> get, Consumer<StyledText> set, boolean preview) {
        this.parent = parent;
        this.area = area;
        this.get = get;
        this.set = set;
        String[] formats = {"B", "U", "I", "cnpcplus.text.clearColor", "cnpcplus.text.clearFormat", "cnpcplus.text.preview"};
        int count = preview ? 6 : 5, w = Math.max(22, area.width / count);
        for (int i = 0; i < count; i++) parent.addButton(new GuiButtonNop(parent, 200 + i, area.x + i * w, y, w - 2, 20, formats[i]));
        int sw = Math.max(8, Math.min(14, (area.width - 100) / COLORS.length)), right = area.x + COLORS.length * sw + 4;
        parent.addTextField(new GuiTextFieldNop(220, parent, right, y + 24, 50, 20, "FFFFFF"));
        parent.addButton(new GuiButtonNop(parent, 221, right + 52, y + 24, 40, 20, "cnpcplus.transporter.editor.color"));
        for (int i = 0; i < COLORS.length; i++) parent.addButton(new GuiButtonNop(parent, 230 + i, area.x + i * sw, y + 24, sw - 1, 20, ""));
    }
    public boolean click(int id) {
        int color = -1, flags = 0;
        boolean toggle = false;
        if (id >= 230 && id < 230 + COLORS.length) {
            color = chosenColor = COLORS[id - 230];
            parent.getTextField(220).setValue(String.format("%06X", chosenColor));
        } else if (id == 221) {
            String hex = parent.getTextField(220).getValue().replace("#", "");
            if (!hex.matches("[0-9a-fA-F]{6}")) return true;
            color = chosenColor = Integer.parseInt(hex, 16);
        } else if (id >= 200 && id <= 204) {
            flags = switch (id) { case 200 -> 1; case 201 -> 2; case 202 -> 4; default -> 0; };
            color = id == 203 ? TransportSpans.CLEAR_COLOR : -1;
            toggle = id != 203;
        } else return false;
        int start = area.getSelectionBeforeText().length(), end = area.getText().length() - area.getSelectionAfterText().length();
        if (area.enabled && end > start) set.accept(get.get().apply(start, end, color, flags, toggle));
        return true;
    }
    public void draw(GuiGraphics graphics) {
        for (int i = 0; i < COLORS.length; i++) {
            GuiButtonNop b = parent.getButton(230 + i);
            if (b != null) graphics.fill(b.getX() + 2, b.getY() + 3, b.getX() + b.getWidth() - 2, b.getY() + 17, 0xFF000000 | COLORS[i]);
        }
    }
}
