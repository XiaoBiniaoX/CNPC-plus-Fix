package bin.cnpcplus.text.client;

import bin.cnpcplus.mixin.script.IGuiTextAreaScriptAccess;
import bin.cnpcplus.text.StyledText;
import java.util.IdentityHashMap;
import java.util.Map;
import noppes.npcs.client.gui.util.CNPCPlusTextLayout;
import noppes.npcs.client.gui.util.GuiTextArea;
import noppes.npcs.client.gui.util.TextContainer;
import noppes.npcs.config.TrueTypeFont;

/** Native input, drag selection and undo; styles update after setText, never inside its callback. */
public final class StyledTextArea extends GuiTextArea {
    private StyledText value;
    private final Map<Object, StyledText> history = new IdentityHashMap<>();
    public StyledTextArea(int id, int x, int y, int width, int height, StyledText value) {
        super(id, x, y, width, height, value.text);
        this.value = value; layout();
    }
    private IGuiTextAreaScriptAccess access() { return (IGuiTextAreaScriptAccess) (Object) this; }
    private void layout() {
        CNPCPlusTextLayout.wrap(access().cnpcplus$container(), IGuiTextAreaScriptAccess.cnpcplus$font(), width, height);
        access().cnpcplus$scroll(Math.max(0, Math.min(access().cnpcplus$scroll(), access().cnpcplus$container().linesCount - access().cnpcplus$container().visibleLines)));
    }
    public void resize(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height; clicked = clickScrolling = false; layout();
    }
    public StyledText value() { return value; }
    public void format(int rgb, int flags, boolean reset) {
        int start = getSelectionBeforeText().length(), end = text.length() - getSelectionAfterText().length();
        apply(start, end, rgb, flags, reset);
    }
    public void apply(int start, int end, int rgb, int flags, boolean reset) {
        if (start == end) return;
        StyledText next = value.apply(start, end, rgb, flags, reset);
        if (!next.spans.equals(value.spans)) history.put(CNPCPlusTextLayout.recordUndo(this, access().cnpcplus$cursor()), value);
        value = next; clicked = false; active = true;
    }
    public void reset(StyledText original) {
        if (original.text.equals(text) && !original.spans.equals(value.spans)) history.put(CNPCPlusTextLayout.recordUndo(this, access().cnpcplus$cursor()), value);
        setText(original.text); value = original;
        access().cnpcplus$cursorPosition(0); access().cnpcplus$selectionStart(0); access().cnpcplus$selectionEnd(0);
        clicked = false;
    }
    @Override public void setText(String next) {
        StyledText previous = value; int count = undoList.size();
        super.setText(next);
        if (value == null) return; // Native constructor calls this override before the draft exists.
        if (undoList.size() > count) history.put(undoList.get(undoList.size() - 1), previous);
        value = value.edit(text); layout();
    }
    @Override public void keyTyped(char character, int key) {
        java.util.List<?> source = key == 44 ? undoList : redoList;
        StyledText restored = active && net.minecraft.client.gui.GuiScreen.isCtrlKeyDown() && (key == 44 || key == 21) && !source.isEmpty()
                ? history.get(source.get(source.size() - 1)) : null;
        StyledText previous = value;
        super.keyTyped(character, key);
        if (restored != null) {
            java.util.List<?> destination = key == 44 ? redoList : undoList;
            history.put(destination.get(destination.size() - 1), previous); value = restored; layout();
        }
    }
    public void drawNativeRow(TrueTypeFont font, int x, int y, int color) {
        TextContainer container = access().cnpcplus$container();
        int index = access().cnpcplus$scroll() + (y - this.y - 1) / container.lineHeight;
        Object row = container.lines.get(index);
        StyledTextRender.draw(font, value, CNPCPlusTextLayout.start(row), CNPCPlusTextLayout.end(row), x, y, container.lineHeight, color);
    }
}
