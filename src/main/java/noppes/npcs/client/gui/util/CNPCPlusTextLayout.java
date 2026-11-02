package noppes.npcs.client.gui.util;

import bin.cnpcplus.text.StyledText;
import noppes.npcs.config.TrueTypeFont;

/** Package bridge for the native package-private LineData; only styled editors call it. */
public final class CNPCPlusTextLayout {
    private CNPCPlusTextLayout() { }
    public static void wrap(TextContainer container, TrueTypeFont font, int width, int height) {
        container.lines.clear();
        for (StyledText.Row row : new StyledText(container.text, "").rows(width - 10, font::width)) {
            int end = row.end;
            if (end == container.text.length() || container.text.charAt(end) == '\n') end++;
            container.lines.add(container.new LineData(row.text, row.start, end));
        }
        container.linesCount = container.lines.size(); container.totalHeight = container.linesCount * container.lineHeight;
        container.visibleLines = Math.max(1, height / container.lineHeight);
    }
    public static int start(Object row) { return ((TextContainer.LineData) row).start; }
    public static Object recordUndo(GuiTextArea area, int cursor) {
        Object record = area.new UndoData(area.text, cursor);
        area.undoList.add((GuiTextArea.UndoData) record); area.redoList.clear(); return record;
    }
    public static int end(Object row) { TextContainer.LineData line = (TextContainer.LineData) row; return line.start + line.text.length(); }
}
