package bin.cnpcplus.script.client;

import bin.cnpcplus.script.ScriptError;
import bin.cnpcplus.script.ScriptErrorAccess;
import top.cnpcplus.api.script.ScriptCheckApi;
import top.cnpcplus.api.script.ScriptCheckApi.Finding;
import top.cnpcplus.api.script.ScriptCompletionApi;
import top.cnpcplus.api.script.ScriptCompletionApi.Request;
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
    private final String language;
    private final List<String> hooks;
    private String checkedText, dismissedText, staticText;
    private int checkedCursor = -1, dismissedCursor = -1, selected, firstVisible;
    private int popupX, popupY, popupWidth, popupRows;
    private ScriptCompletionApi.Result result;
    private List<String> hints = List.of();
    private String completionError = "";
    private long completionRevision = -1;
    private TextContainer marked;
    private ScriptError markedRuntime;
    private List<Finding> markedWarnings = List.of(), staticDiagnostics = List.of();
    private boolean jumped;
    private String status = "", checkStatus = "", referenceText = "";
    private int statusColor, referenceScroll, referenceRows;
    private boolean referenceHovered;

    public ScriptTextArea(int id, int x, int y, int width, int height, String text,
                          ScriptContainer owner, String context, String language, List<String> hooks) {
        super(id, x, y, width, Math.max(24, height - STATUS_HEIGHT), text);
        this.owner = owner; this.context = context;
        this.hooks = hooks == null ? List.of() : new ArrayList<>(hooks);
        this.language = language == null ? "" : language;
    }
    private TextAreaSelectionAccess access() { return (TextAreaSelectionAccess)(Object)this; }
    private Font font() { return Minecraft.getInstance().font; }
    public boolean hasPopup() { return this.active && this.result != null && !this.result.suggestions().isEmpty(); }
    private boolean supportsCompletion() { return ScriptCompletionApi.supports(language); }
    private String completionHelp() {
        var profile = ScriptCompletionApi.language(language);
        return profile != null && !profile.helpText().isEmpty() ? profile.helpText()
                : I18n.get(supportsCompletion() ? "cnpcplus.script.completion_help" : "cnpcplus.script.completion_js");
    }
    private ScriptError runtime() { return owner instanceof ScriptErrorAccess access ? access.cnpcplus$error() : null; }

    private void refresh(boolean force) {
        int cursor = access().cnpcplus$cursor();
        long revision = ScriptCompletionApi.revision();
        if (!force && revision == completionRevision && cursor == checkedCursor && getText().equals(checkedText)) return;
        completionRevision = revision;
        checkedCursor = cursor; checkedText = getText(); selected = firstVisible = 0;
        result = ScriptCompletionApi.complete(new Request(checkedText, cursor, language, context, hooks, force));
        hints = result.signatures();
        completionError = result.error();
        if (!force && cursor == dismissedCursor && checkedText.equals(dismissedText)) result = null;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!active || !isEnabled()) return super.keyPressed(key, scan, modifiers);
        if (key == GLFW.GLFW_KEY_F4) { jumpToError(); return true; }
        if (supportsCompletion() && key == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) { refresh(true); return true; }
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
        if (active && supportsCompletion() && character == ' ' && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return true;
        boolean handled = super.charTyped(character, modifiers); refresh(false); return handled;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (referenceHovered) { referenceScroll = Math.max(0, referenceScroll - (int)Math.signum(vertical)); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    private void moveSelection(int direction) {
        int size = result.suggestions().size(); selected = Math.floorMod(selected + direction, size);
        if (selected < firstVisible) firstVisible = selected;
        int rows = Math.max(1, popupRows);
        if (selected >= firstVisible + rows) firstVisible = selected - rows + 1;
        firstVisible = Math.max(0, Math.min(firstVisible, Math.max(0, size - rows)));
    }
    private void dismiss() { dismissedCursor = access().cnpcplus$cursor(); dismissedText = getText(); result = null; }
    private void accept() {
        if (!hasPopup()) return;
        if (!getText().equals(checkedText) || access().cnpcplus$cursor() != checkedCursor
                || completionRevision != ScriptCompletionApi.revision()) { refresh(true); return; }
        var edit = result.suggestions().get(selected);
        access().cnpcplus$select(edit.start(), edit.end(), edit.end());
        addText(edit.insertText());
        int cursor = edit.start() + edit.caret();
        access().cnpcplus$select(cursor, cursor, cursor);
        clicked = false; checkedCursor = -1; refresh(false); dismiss();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && result != null && popupRows > 0 && x >= popupX && x < popupX + popupWidth
                && y >= popupY && y < popupY + popupRows * ROW_HEIGHT) {
            selected = Math.min(firstVisible + (int)(y - popupY) / ROW_HEIGHT, result.suggestions().size() - 1);
            accept(); return true;
        }
        boolean handled = super.mouseClicked(x, y, button); checkedCursor = -1; return handled;
    }

    private ScriptError diagnostic() {
        ScriptError runtime = runtime();
        return runtime != null && runtime.matches(getText()) ? runtime : null;
    }
    /** Button-only lint: no executor, owner, console or diagnostic-NBT writes. */
    public void simpleCheck() {
        staticText = getText();
        staticDiagnostics = owner == null ? List.of() : ScriptCheckApi.analyze(
                new ScriptCheckApi.Source(staticText, language, ScriptContainer.Data.keySet()), owner.scripts.isEmpty(), Set.copyOf(hooks));
        long errors = staticDiagnostics.stream().filter(item -> item.severity() == ScriptCheckApi.Severity.ERROR).count();
        long warnings = staticDiagnostics.stream().filter(item -> item.severity() == ScriptCheckApi.Severity.WARN).count();
        long infos = staticDiagnostics.stream().filter(item -> item.severity() == ScriptCheckApi.Severity.INFO).count();
        checkStatus = owner == null || !ScriptCompletionApi.usesBuiltinChecks(language) && staticDiagnostics.isEmpty() ? I18n.get("cnpcplus.script.check_skipped")
                : staticDiagnostics.isEmpty() ? I18n.get("cnpcplus.script.check_clear")
                : I18n.get("cnpcplus.script.check_summary", errors, warnings, infos);
        statusColor = errors > 0 ? 0xFFFF6666 : warnings > 0 ? 0xFFFFD45A : infos > 0 ? 0xFF70B8FF : 0xFF202020;
        marked = null;
    }
    private void markError() {
        if (staticText != null && !getText().equals(staticText)) {
            staticText = null; staticDiagnostics = List.of(); checkStatus = ""; marked = null;
        }
        ScriptError runtime = runtime(); TextContainer container = access().cnpcplus$container();
        if (container == marked && runtime == markedRuntime && staticDiagnostics == markedWarnings) return;
        marked = container; markedRuntime = runtime; markedWarnings = staticDiagnostics;
        container.makeup.clear(); container.formatCodeText();
        status = checkStatus;
        for (Finding finding : staticDiagnostics) {
            char color = switch (finding.severity()) { case ERROR -> 'c'; case WARN -> 'e'; case INFO -> 'b'; };
            int priority = switch (finding.severity()) { case ERROR -> 100; case WARN -> 90; case INFO -> 80; };
            if (finding.length() > 0) container.addMakeUp(finding.offset(), finding.offset() + finding.length(), color, priority);
        }
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
        if (range == null && staticDiagnostics.isEmpty()) return;
        int position = range != null ? range[0] : staticDiagnostics.getFirst().offset();
        access().cnpcplus$select(position, position, position);
        var container = access().cnpcplus$container();
        for (int row = 0; row < container.lines.size(); row++) {
            TextLineAccess line = (TextLineAccess)container.lines.get(row);
            if (position >= line.cnpcplus$start() && position < line.cnpcplus$end()) {
                access().cnpcplus$scroll(row - 2); break;
            }
        }
    }
    private String warning(Finding diagnostic) {
        int line = 1;
        for (int i = 0; i < diagnostic.offset(); i++) if (getText().charAt(i) == '\n') line++;
        String severity = I18n.get("cnpcplus.script.severity." + diagnostic.severity().name().toLowerCase(Locale.ROOT));
        return severity + ": " + I18n.get(diagnostic.translationKey(), line, diagnostic.subject());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!visible) return;
        markError(); super.render(graphics, mouseX, mouseY); refresh(false);
        String help = completionHelp();
        graphics.drawString(font(), font().plainSubstrByWidth(status.isEmpty() ? help : status, width),
                x, y + height + 3, status.isEmpty() ? 0xFF202020 : statusColor, false);
        String detail = !completionError.isEmpty() ? completionError : hints.isEmpty() ? (status.isEmpty() ? "" : help) : hints.getFirst();
        graphics.drawString(font(), font().plainSubstrByWidth(detail, width), x, y + height + 14, 0xFF202020, false);
        popupRows = 0; referenceHovered = false;
        if (hasPopup()) drawPopup(graphics, mouseX, mouseY);
        drawStatusTooltip(graphics, mouseX, mouseY);
    }
    private void drawStatusTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y + height || mouseY >= y + height + 24) return;
        List<String> lines = new ArrayList<>(); ScriptError error = diagnostic();
        if ((error != null || !staticDiagnostics.isEmpty()) && mouseY < y + height + 12) {
            if (!status.isEmpty()) lines.add(status);
            if (error != null) lines.add(error.message());
            for (Finding finding : staticDiagnostics) lines.add(warning(finding));
        } else {
            lines.add(completionHelp());
            if (!completionError.isEmpty()) lines.add(completionError);
            if (!checkStatus.isEmpty()) lines.add(checkStatus);
            lines.addAll(hints);
        }
        if (!lines.isEmpty()) drawContainedTooltip(graphics, String.join("\n", lines), y + height - 4);
    }
    private void drawPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        int maxRows = Math.max(1, Math.min(POPUP_ROWS, (height - 36) / ROW_HEIGHT));
        firstVisible = Math.max(0, Math.min(firstVisible, Math.max(0, result.suggestions().size() - maxRows)));
        popupRows = Math.min(maxRows, result.suggestions().size() - firstVisible);
        popupWidth = Math.min(width - 4, 330); popupX = x + width - popupWidth - 2;
        popupY = y + height - popupRows * ROW_HEIGHT - 2;
        graphics.fill(popupX - 1, popupY - 1, popupX + popupWidth + 1, popupY + popupRows * ROW_HEIGHT + 1, 0xFF547689);
        graphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupRows * ROW_HEIGHT, 0xFA15222C);
        for (int row = 0; row < popupRows; row++) {
            int index = firstVisible + row;
            if (index == selected) graphics.fill(popupX, popupY + row * ROW_HEIGHT, popupX + popupWidth, popupY + (row + 1) * ROW_HEIGHT, 0xFF304B5B);
            var entry = result.suggestions().get(index);
            graphics.drawString(font(), font().plainSubstrByWidth(entry.label(), popupWidth - 6),
                    popupX + 3, popupY + row * ROW_HEIGHT + 2, entry.label().contains("(") ? 0xFFFFE1A0 : 0xFF88DEFF, false);
        }
        if (mouseX >= popupX && mouseX < popupX + popupWidth && mouseY >= popupY && mouseY < popupY + popupRows * ROW_HEIGHT) {
            var entry = result.suggestions().get(firstVisible + (mouseY - popupY) / ROW_HEIGHT);
            drawContainedTooltip(graphics, entry.detail(), popupY - 3);
        }
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
