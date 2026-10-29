package bin.cnpcplus.script.client;

import bin.cnpcplus.mixin.script.IGuiTextAreaScriptAccess;
import bin.cnpcplus.script.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiUtils;
import noppes.npcs.client.gui.util.GuiTextArea;
import noppes.npcs.client.gui.util.TextContainer;
import noppes.npcs.controllers.ScriptContainer;
import org.lwjgl.input.Keyboard;

/** Only script editors use this subclass; other CNPC text fields are untouched. */
public final class ScriptTextArea extends GuiTextArea {
    private final ScriptContainer owner;
    private final String context;
    private final boolean javascript;
    private String checkedText = null, dismissedText = null;
    private int checkedCursor = -1, dismissedCursor = -1, selected, popupX, popupY, popupWidth, popupRows;
    private ScriptApiCompletion.Result result;
    private List<ScriptApiCompletion.Entry> hints = Collections.emptyList();
    private TextContainer marked;
    private boolean jumped;
    private String status = "";

    public ScriptTextArea(int id, int x, int y, int width, int height, String text, ScriptContainer owner, String context, String language) {
        super(id, x, y, width, Math.max(24, height - 24), text);
        this.owner = owner; this.context = context;
        String name = language == null ? "" : language.toLowerCase(Locale.ROOT);
        javascript = name.contains("ecma") || name.contains("javascript") || name.contains("nashorn") || name.equals("js");
    }
    private IGuiTextAreaScriptAccess access() { return (IGuiTextAreaScriptAccess) (Object) this; }
    private FontRenderer font() { return Minecraft.getMinecraft().fontRenderer; }
    public boolean hasPopup() { return active && result != null && !result.entries.isEmpty(); }

    private void refresh(boolean force) {
        int cursor = access().cnpcplus$cursor();
        if (!force && cursor == checkedCursor && getText().equals(checkedText)) return;
        checkedCursor = cursor; checkedText = getText(); selected = 0;
        hints = javascript ? ScriptApiCompletion.hint(checkedText, cursor, context) : Collections.emptyList();
        result = javascript ? ScriptApiCompletion.complete(checkedText, cursor, context) : null;
        if (!force && result != null && result.start == cursor && (cursor == 0 || checkedText.charAt(cursor - 1) != '.')) result = null;
        if (!force && cursor == dismissedCursor && checkedText.equals(dismissedText)) result = null;
    }

    @Override public void keyTyped(char c, int key) {
        if (!active || !isEnabled()) { super.keyTyped(c, key); return; }
        if (key == Keyboard.KEY_F4) { jumpToError(); return; }
        if (javascript && key == Keyboard.KEY_SPACE && GuiScreen.isCtrlKeyDown()) { refresh(true); return; }
        refresh(false);
        if (result != null && !result.entries.isEmpty()) {
            if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
                selected = Math.floorMod(selected + (key == Keyboard.KEY_UP ? -1 : 1), result.entries.size()); return;
            }
            if (key == Keyboard.KEY_TAB && !GuiScreen.isShiftKeyDown()) { accept(); return; }
            if (key == Keyboard.KEY_ESCAPE) { dismiss(); return; }
        }
        super.keyTyped(c, key);
        refresh(false);
    }
    private void dismiss() { dismissedCursor = access().cnpcplus$cursor(); dismissedText = getText(); result = null; }
    private void accept() {
        if (result == null || result.entries.isEmpty()) return;
        ScriptApiCompletion.Entry entry = result.entries.get(selected);
        IGuiTextAreaScriptAccess a = access();
        int end = a.cnpcplus$cursor();
        // Replace the whole identifier if the caret is in its middle. Native addText preserves undo/redo.
        while (end < getText().length() && Character.isJavaIdentifierPart(getText().charAt(end))) end++;
        a.cnpcplus$cursor(result.start, false); a.cnpcplus$cursor(end, true);
        String insertion = entry.insert;
        if (end < getText().length() && getText().charAt(end) == '(' && entry.method) insertion = entry.name;
        a.cnpcplus$insert(insertion);
        clicked = false; checkedCursor = -1; refresh(false); dismiss();
    }

    @Override public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && result != null && popupRows > 0 && mouseX >= popupX && mouseX < popupX + popupWidth
                && mouseY >= popupY && mouseY < popupY + popupRows * 12) {
            selected = Math.max(0, selected - selected % 6) + (mouseY - popupY) / 12;
            selected = Math.min(selected, result.entries.size() - 1); accept(); return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        checkedCursor = -1; return handled;
    }

    private ScriptDiagnostic diagnostic() { return ScriptDiagnostics.get(owner); }
    private void markError() {
        ScriptDiagnostic d = diagnostic();
        TextContainer container = access().cnpcplus$container();
        if (container == marked) return;
        marked = container;
        status = "";
        if (d == null) return;
        if (!d.matches(getText())) { status = I18n.format("cnpcplus.script.error_stale"); return; }
        int[] range = ScriptDiagnostic.range(getText(), d.line);
        if (range[0] < 0 || d.line == 0) { status = I18n.format("cnpcplus.script.error_external"); return; }
        status = I18n.format("cnpcplus.script.error_line", d.line);
        if (range[1] > range[0]) container.addMakeUp(range[0], range[1], 'c', 100);
        if (!jumped) { jumpToError(); jumped = true; }
    }
    private void jumpToError() {
        ScriptDiagnostic d = diagnostic();
        if (d == null || !d.matches(getText())) return;
        int[] range = ScriptDiagnostic.range(getText(), d.line);
        if (range[0] < 0) return;
        IGuiTextAreaScriptAccess a = access();
        a.cnpcplus$cursor(range[0], false);
        TextContainer container = a.cnpcplus$container();
        // LineData is package-private. Read its actual wrapped-row start; never assume a fixed-width font.
        try {
            for (int i = 0; i < container.lines.size(); i++) {
                Object row = container.lines.get(i);
                Field start = row.getClass().getDeclaredField("start"); start.setAccessible(true);
                Field end = row.getClass().getDeclaredField("end"); end.setAccessible(true);
                if (range[0] >= start.getInt(row) && range[0] < end.getInt(row)) {
                    a.cnpcplus$scroll(Math.max(0, Math.min(i - 2, container.linesCount - container.visibleLines))); break;
                }
            }
        } catch (ReflectiveOperationException ignored) { /* Text marker and F4 caret still work if CNPC changes row internals. */ }
    }

    @Override public void drawScreen(int mouseX, int mouseY) {
        if (!visible) return;
        markError();
        super.drawScreen(mouseX, mouseY);
        refresh(false);
        FontRenderer f = font();
        String help = I18n.format(javascript ? "cnpcplus.script.completion_help" : "cnpcplus.script.completion_js");
        f.drawString(f.trimStringToWidth(status.isEmpty() ? help : status, width), x, y + height + 3, status.isEmpty() ? 0xA0BBC6 : 0xFF6666);
        String detail = hints.isEmpty() ? status.isEmpty() ? "" : help : hints.get(0).signature;
        f.drawString(f.trimStringToWidth(detail, width), x, y + height + 14, 0xFFDF88);
        popupRows = 0;
        if (active && result != null && !result.entries.isEmpty()) drawPopup(mouseX, mouseY);
        if (mouseX >= x && mouseX <= x + width && mouseY >= y + height && mouseY < y + height + 24) {
            List<String> lines = new ArrayList<>();
            ScriptDiagnostic d = diagnostic();
            if (d != null && mouseY < y + height + 12) { lines.add(status); lines.addAll(f.listFormattedStringToWidth(d.message, 300)); }
            else for (ScriptApiCompletion.Entry hint : hints) lines.add(hint.signature);
            if (!lines.isEmpty()) GuiUtils.drawHoveringText(lines, mouseX, mouseY, Minecraft.getMinecraft().currentScreen.width,
                    Minecraft.getMinecraft().currentScreen.height, 320, f);
        }
    }
    private void drawPopup(int mouseX, int mouseY) {
        int first = selected - selected % 6;
        popupRows = Math.min(6, result.entries.size() - first);
        popupWidth = Math.min(width - 4, 330);
        popupX = x + width - popupWidth - 2; popupY = y + height - popupRows * 12 - 2;
        drawRect(popupX - 1, popupY - 1, popupX + popupWidth + 1, popupY + popupRows * 12 + 1, 0xFF547689);
        drawRect(popupX, popupY, popupX + popupWidth, popupY + popupRows * 12, 0xFA15222C);
        for (int row = 0; row < popupRows; row++) {
            int index = first + row;
            if (index == selected) drawRect(popupX, popupY + row * 12, popupX + popupWidth, popupY + (row + 1) * 12, 0xFF304B5B);
            ScriptApiCompletion.Entry e = result.entries.get(index);
            font().drawString(font().trimStringToWidth(e.signature, popupWidth - 6), popupX + 3, popupY + row * 12 + 2, e.method ? 0xFFE1A0 : 0x88DEFF);
        }
        if (mouseX >= popupX && mouseX < popupX + popupWidth && mouseY >= popupY && mouseY < popupY + popupRows * 12) {
            ScriptApiCompletion.Entry e = result.entries.get(first + (mouseY - popupY) / 12);
            GuiUtils.drawHoveringText(Collections.singletonList(e.signature), mouseX, mouseY,
                    Minecraft.getMinecraft().currentScreen.width, Minecraft.getMinecraft().currentScreen.height, 360, font());
        }
    }
}
