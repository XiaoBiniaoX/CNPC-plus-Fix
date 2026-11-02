package bin.cnpcplus.script.client;

import bin.cnpcplus.script.ScriptError;
import bin.cnpcplus.script.ScriptErrorAccess;
import bin.cnpcplus.script.ScriptStaticCheck;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.TextContainer;
import org.lwjgl.glfw.GLFW;
import top.cnpcplus.mixin.TextLineAccess;
import top.cnpcplus.transport.client.TextAreaSelectionAccess;

/** Script-only controls; native text/selection/clipboard/undo and diagnostic NBT remain authoritative. */
@OnlyIn(Dist.CLIENT)
public final class ScriptTextArea extends GuiTextArea {
    private static final int STATUS_HEIGHT = 48, POPUP_ROWS = 6, ROW_HEIGHT = 12;
    private final ScriptContainer owner;
    private final String context;
    private final boolean javascript;
    private final List<String> hooks;
    private String checkedText, dismissedText, staticText;
    private int checkedCursor = -1, dismissedCursor = -1, selected, firstVisible;
    private int popupX, popupY, popupWidth, popupRows;
    private ApiCompletion.Result result;
    private List<ApiCompletion.Entry> hints = List.of();
    private TextContainer marked;
    private ScriptError markedRuntime;
    private ScriptStaticCheck.Result markedWarning, staticDiagnostic;
    private boolean jumped;
    private String status = "", checkStatus = "", referenceText = "";
    private int statusColor, referenceScroll, referenceRows;
    private boolean referenceHovered;

    public ScriptTextArea(int id, int x, int y, int width, int height, String text,
                          ScriptContainer owner, String context, String language, List<String> hooks) {
        super(id, x, y, width, Math.max(24, height - STATUS_HEIGHT), text);
        this.owner = owner; this.context = context;
        this.hooks = hooks == null ? List.of() : new ArrayList<>(hooks);
        String name = language == null ? "" : language.toLowerCase(Locale.ROOT);
        this.javascript = name.contains("ecma") || name.contains("javascript") || name.contains("nashorn") || name.equals("js");
    }
    private TextAreaSelectionAccess access() { return (TextAreaSelectionAccess)(Object)this; }
    private Font font() { return Minecraft.getInstance().font; }
    public boolean hasPopup() { return this.active && this.result != null && !this.result.entries().isEmpty(); }
    private ScriptError runtime() { return owner instanceof ScriptErrorAccess access ? access.cnpcplus$error() : null; }

    private void refresh(boolean force) {
        int cursor = access().cnpcplus$cursor();
        if (!force && cursor == checkedCursor && getText().equals(checkedText)) return;
        checkedCursor = cursor; checkedText = getText(); selected = firstVisible = 0;
        hints = javascript ? ApiCompletion.hint(checkedText, cursor, context) : List.of();
        result = javascript ? ApiCompletion.complete(checkedText, cursor, context, hooks) : null;
        if (!force && result != null && result.start() == cursor && (cursor == 0 || checkedText.charAt(cursor - 1) != '.')) result = null;
        if (!force && cursor == dismissedCursor && checkedText.equals(dismissedText)) result = null;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!active || !isEnabled()) return super.keyPressed(key, scan, modifiers);
        if (key == GLFW.GLFW_KEY_F4) { jumpToError(); return true; }
        if (javascript && key == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) { refresh(true); return true; }
        refresh(false);
        if (hasPopup()) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                moveSelection(key == GLFW.GLFW_KEY_UP ? -1 : 1); return true;
            }
            if (key == GLFW.GLFW_KEY_TAB && !Screen.hasShiftDown()) { accept(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { dismiss(); return true; }
        }
        boolean handled = super.keyPressed(key, scan, modifiers); refresh(false); return handled;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (active && javascript && character == ' ' && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return true;
        boolean handled = super.charTyped(character, modifiers); refresh(false); return handled;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (referenceHovered) { referenceScroll = Math.max(0, referenceScroll - (int)Math.signum(vertical)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    private void moveSelection(int direction) {
        int size = result.entries().size(); selected = Math.floorMod(selected + direction, size);
        if (selected < firstVisible) firstVisible = selected;
        int rows = Math.max(1, popupRows);
        if (selected >= firstVisible + rows) firstVisible = selected - rows + 1;
        firstVisible = Math.max(0, Math.min(firstVisible, Math.max(0, size - rows)));
    }
    private void dismiss() { dismissedCursor = access().cnpcplus$cursor(); dismissedText = getText(); result = null; }
    private void accept() {
        if (result == null || result.entries().isEmpty()) return;
        var entry = result.entries().get(selected);
        int end = access().cnpcplus$cursor();
        while (end < getText().length() && Character.isJavaIdentifierPart(getText().charAt(end))) end++;
        access().cnpcplus$select(result.start(), end, end);
        String insertion = entry.insert();
        if (end < getText().length() && getText().charAt(end) == '(' && entry.method()) insertion = entry.name();
        addText(insertion); clicked = false; checkedCursor = -1; refresh(false); dismiss();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && result != null && popupRows > 0 && x >= popupX && x < popupX + popupWidth
                && y >= popupY && y < popupY + popupRows * ROW_HEIGHT) {
            selected = Math.min(firstVisible + (int)(y - popupY) / ROW_HEIGHT, result.entries().size() - 1);
            accept(); return true;
        }
        boolean handled = super.mouseClicked(x, y, button); checkedCursor = -1; return handled;
    }

    private ScriptError diagnostic() {
        ScriptError runtime = runtime();
        return runtime != null && runtime.matches(getText()) ? runtime : staticDiagnostic == null ? null : staticDiagnostic.error();
    }
    /** Button-only lint: no executor, owner, console or diagnostic-NBT writes. */
    public void simpleCheck() {
        staticText = getText();
        staticDiagnostic = javascript && owner != null
                ? ScriptStaticCheck.check(staticText, ScriptContainer.Data.keySet(), owner.scripts.isEmpty()) : null;
        checkStatus = !javascript || owner == null ? I18n.get("cnpcplus.script.check_skipped")
                : staticDiagnostic != null ? warning(staticDiagnostic)
                : !owner.scripts.isEmpty() ? I18n.get("cnpcplus.script.check_brackets_only") : I18n.get("cnpcplus.script.check_clear");
        marked = null;
    }
    private void markError() {
        if (staticText != null && !getText().equals(staticText)) {
            staticText = null; staticDiagnostic = null; checkStatus = ""; marked = null;
        }
        ScriptError runtime = runtime(); TextContainer container = access().cnpcplus$container();
        if (container == marked && runtime == markedRuntime && staticDiagnostic == markedWarning) return;
        marked = container; markedRuntime = runtime; markedWarning = staticDiagnostic;
        container.makeup.clear(); container.formatCodeText();
        status = checkStatus; statusColor = 0xFF202020;
        if (staticDiagnostic != null) container.addMakeUp(staticDiagnostic.start(), staticDiagnostic.end(), 'e', 90);
        if (runtime == null) return;
        if (!runtime.matches(getText())) { status = I18n.get("cnpcplus.script.error_stale"); statusColor = 0xFFFF6666; return; }
        statusColor = 0xFFFF6666;
        int[] range = runtime.range(getText());
        if (range == null) { status = I18n.get("cnpcplus.script.error_external"); return; }
        status = I18n.get("cnpcplus.script.error_line", runtime.line());
        container.addMakeUp(range[0], range[1], 'c', Integer.MAX_VALUE);
        if (!jumped) { jumpToError(); jumped = true; }
    }
    private void jumpToError() {
        ScriptError error = diagnostic();
        int[] range = error == null ? null : error.range(getText());
        if (range == null) return;
        int position = staticDiagnostic != null && error == staticDiagnostic.error() ? staticDiagnostic.start() : range[0];
        access().cnpcplus$select(position, position, position);
        var container = access().cnpcplus$container();
        for (int row = 0; row < container.lines.size(); row++) {
            TextLineAccess line = (TextLineAccess)container.lines.get(row);
            if (position >= line.cnpcplus$start() && position < line.cnpcplus$end()) {
                access().cnpcplus$scroll(row - 2); break;
            }
        }
    }
    private static String warning(ScriptStaticCheck.Result diagnostic) {
        return I18n.get(diagnostic.key(), diagnostic.error().line(), diagnostic.token());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!visible) return;
        markError(); super.render(graphics, mouseX, mouseY); refresh(false);
        String help = I18n.get(javascript ? "cnpcplus.script.completion_help" : "cnpcplus.script.completion_js");
        graphics.drawString(font(), font().plainSubstrByWidth(status.isEmpty() ? help : status, width),
                x, y + height + 3, status.isEmpty() ? 0xFF202020 : statusColor, false);
        String detail = hints.isEmpty() ? (status.isEmpty() ? "" : help) : hints.getFirst().signature();
        graphics.drawString(font(), font().plainSubstrByWidth(detail, width), x, y + height + 14, 0xFF202020, false);
        popupRows = 0; referenceHovered = false;
        if (hasPopup()) drawPopup(graphics, mouseX, mouseY);
        drawStatusTooltip(graphics, mouseX, mouseY);
    }
    private void drawStatusTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y + height || mouseY >= y + height + 24) return;
        List<String> lines = new ArrayList<>(); ScriptError error = diagnostic();
        if (error != null && mouseY < y + height + 12) {
            if (!status.isEmpty()) lines.add(status);
            lines.add(staticDiagnostic != null && error == staticDiagnostic.error() ? warning(staticDiagnostic) : error.message());
            if (staticDiagnostic != null && error != staticDiagnostic.error()) lines.add(warning(staticDiagnostic));
        } else {
            lines.add(I18n.get(javascript ? "cnpcplus.script.completion_help" : "cnpcplus.script.completion_js"));
            if (!checkStatus.isEmpty()) lines.add(checkStatus);
            for (var hint : hints) lines.add(hint.signature());
        }
        if (!lines.isEmpty()) drawContainedTooltip(graphics, String.join("\n", lines), y + height - 4);
    }
    private void drawPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        int maxRows = Math.max(1, Math.min(POPUP_ROWS, (height - 36) / ROW_HEIGHT));
        firstVisible = Math.max(0, Math.min(firstVisible, Math.max(0, result.entries().size() - maxRows)));
        popupRows = Math.min(maxRows, result.entries().size() - firstVisible);
        popupWidth = Math.min(width - 4, 330); popupX = x + width - popupWidth - 2;
        popupY = y + height - popupRows * ROW_HEIGHT - 2;
        graphics.fill(popupX - 1, popupY - 1, popupX + popupWidth + 1, popupY + popupRows * ROW_HEIGHT + 1, 0xFF547689);
        graphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupRows * ROW_HEIGHT, 0xFA15222C);
        for (int row = 0; row < popupRows; row++) {
            int index = firstVisible + row;
            if (index == selected) graphics.fill(popupX, popupY + row * ROW_HEIGHT, popupX + popupWidth, popupY + (row + 1) * ROW_HEIGHT, 0xFF304B5B);
            var entry = result.entries().get(index);
            graphics.drawString(font(), font().plainSubstrByWidth(entry.signature(), popupWidth - 6),
                    popupX + 3, popupY + row * ROW_HEIGHT + 2, entry.method() ? 0xFFFFE1A0 : 0xFF88DEFF, false);
        }
        if (mouseX >= popupX && mouseX < popupX + popupWidth && mouseY >= popupY && mouseY < popupY + popupRows * ROW_HEIGHT)
            drawContainedTooltip(graphics, result.entries().get(firstVisible + (mouseY - popupY) / ROW_HEIGHT).signature(), popupY - 3);
    }
    private void drawContainedTooltip(GuiGraphics graphics, String text, int bottom) {
        referenceHovered = true;
        if (!text.equals(referenceText)) { referenceText = text; referenceScroll = 0; }
        List<FormattedCharSequence> lines = font().split(Component.literal(text), width - 12);
        referenceRows = Math.max(1, Math.min(lines.size(), (bottom - Math.max(4, y) - 8) / font().lineHeight));
        referenceScroll = Math.min(referenceScroll, Math.max(0, lines.size() - referenceRows));
        int boxWidth = 0;
        for (var line : lines) boxWidth = Math.max(boxWidth, font().width(line));
        int h = referenceRows * font().lineHeight + 8, yy = bottom - h;
        int xx = Math.max(4, Math.min(x + width - boxWidth - 8, Minecraft.getInstance().getWindow().getGuiScaledWidth() - boxWidth - 12));
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 400);
        graphics.fill(xx, yy, xx + boxWidth + 8, yy + h, 0xFF181818);
        for (int i = 0; i < referenceRows; i++) graphics.drawString(font(), lines.get(referenceScroll + i), xx + 4, yy + 4 + i * font().lineHeight, 0xFFF0F0F0, false);
        if (lines.size() > referenceRows) graphics.fill(xx + boxWidth + 6, yy + 4, xx + boxWidth + 8, yy + h - 4, 0xFFAAAAAA);
        graphics.pose().popPose();
    }
}
