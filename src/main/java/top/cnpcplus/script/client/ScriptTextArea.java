package top.cnpcplus.script.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.TextContainer;
import org.lwjgl.glfw.GLFW;
import top.cnpcplus.mixin.IGuiTextAreaScriptAccess;
import top.cnpcplus.script.ScriptApiCompletion;
import top.cnpcplus.script.ScriptDiagnostic;
import top.cnpcplus.script.ScriptDiagnostics;

/** Script-only text area with CNPC API completion and persisted runtime-error highlighting. */
@OnlyIn(Dist.CLIENT)
public final class ScriptTextArea extends GuiTextArea {
    private static final int STATUS_HEIGHT = 48;
    private static final int POPUP_ROWS = 6;
    private static final int POPUP_ROW_HEIGHT = 12;

    private final ScriptContainer owner;
    private final String context;
    private final boolean javascript;
    private final List<String> hooks;
    private String checkedText;
    private String dismissedText;
    private int checkedCursor = -1;
    private int dismissedCursor = -1;
    private int selected;
    private int firstVisible;
    private int popupX;
    private int popupY;
    private int popupWidth;
    private int popupRows;
    private ScriptApiCompletion.Result result;
    private List<ScriptApiCompletion.Entry> hints = Collections.emptyList();
    private TextContainer marked;
    private ScriptDiagnostic markedRuntime;
    private ScriptDiagnostic markedWarning;
    private boolean jumped;
    private String status = "";
    private int statusColor = 0xFFA0BBC6;
    private String staticText;
    private ScriptDiagnostic staticDiagnostic;
    private String checkStatus = "";
    private String referenceText = "";
    private int referenceScroll, referenceRows;
    private boolean referenceHovered;

    public ScriptTextArea(int id, int x, int y, int width, int height, String text,
                          ScriptContainer owner, String context, String language, List<String> hooks) {
        super(id, x, y, width, Math.max(24, height - STATUS_HEIGHT), text);
        this.owner = owner;
        this.context = context;
        this.hooks = hooks == null ? Collections.emptyList() : new ArrayList<>(hooks);
        String name = language == null ? "" : language.toLowerCase(Locale.ROOT);
        this.javascript = name.contains("ecma") || name.contains("javascript")
                || name.contains("nashorn") || name.equals("js");
    }

    private IGuiTextAreaScriptAccess access() {
        return (IGuiTextAreaScriptAccess) (Object) this;
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    public boolean hasPopup() {
        return this.active && this.result != null && !this.result.entries.isEmpty();
    }

    private void refresh(boolean force) {
        int cursor = access().cnpcplus$cursor();
        if (!force && cursor == this.checkedCursor && getText().equals(this.checkedText)) return;
        this.checkedCursor = cursor;
        this.checkedText = getText();
        this.selected = 0;
        this.firstVisible = 0;
        this.hints = this.javascript
                ? ScriptApiCompletion.hint(this.checkedText, cursor, this.context)
                : Collections.emptyList();
        this.result = this.javascript
                ? ScriptApiCompletion.complete(this.checkedText, cursor, this.context, this.hooks)
                : null;
        if (!force && this.result != null && this.result.start == cursor
                && (cursor == 0 || this.checkedText.charAt(cursor - 1) != '.')) {
            this.result = null;
        }
        if (!force && cursor == this.dismissedCursor && this.checkedText.equals(this.dismissedText)) {
            this.result = null;
        }
    }

    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        if (!this.active || !isEnabled()) return super.m_7933_(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_F4) {
            jumpToError();
            return true;
        }
        if (this.javascript && keyCode == GLFW.GLFW_KEY_SPACE && Screen.hasControlDown()) {
            refresh(true);
            return true;
        }

        refresh(false);
        if (this.result != null && !this.result.entries.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                moveSelection(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB && !Screen.hasShiftDown()) {
                accept();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                dismiss();
                return true;
            }
        }

        boolean handled = super.m_7933_(keyCode, scanCode, modifiers);
        refresh(false);
        return handled;
    }

    @Override
    public boolean m_5534_(char character, int modifiers) {
        // GLFW delivers Ctrl+Space as a separate character event after keyPressed.
        if (this.active && this.javascript && character == ' ' && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return true;
        boolean handled = super.m_5534_(character, modifiers);
        refresh(false);
        return handled;
    }

    @Override public boolean m_6050_(double x, double y, double delta) {
        if (this.referenceHovered) {
            this.referenceScroll = Math.max(0, this.referenceScroll - (int)Math.signum(delta));
            return true;
        }
        return super.m_6050_(x, y, delta);
    }

    private void moveSelection(int direction) {
        int size = this.result.entries.size();
        this.selected = Math.floorMod(this.selected + direction, size);
        if (this.selected < this.firstVisible) this.firstVisible = this.selected;
        int rows = Math.max(1, this.popupRows);
        if (this.selected >= this.firstVisible + rows) {
            this.firstVisible = this.selected - rows + 1;
        }
        this.firstVisible = Math.max(0, Math.min(this.firstVisible, Math.max(0, size - rows)));
    }

    private void dismiss() {
        this.dismissedCursor = access().cnpcplus$cursor();
        this.dismissedText = getText();
        this.result = null;
    }

    private void accept() {
        if (this.result == null || this.result.entries.isEmpty()) return;
        ScriptApiCompletion.Entry entry = this.result.entries.get(this.selected);
        IGuiTextAreaScriptAccess access = access();
        int end = access.cnpcplus$cursor();
        while (end < getText().length() && Character.isJavaIdentifierPart(getText().charAt(end))) end++;
        access.cnpcplus$cursor(this.result.start, false);
        access.cnpcplus$cursor(end, true);
        String insertion = entry.insert;
        if (end < getText().length() && getText().charAt(end) == '(' && entry.method) insertion = entry.name;
        addText(insertion);
        this.clicked = false;
        this.checkedCursor = -1;
        refresh(false);
        dismiss();
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        if (button == 0 && this.result != null && this.popupRows > 0
                && mouseX >= this.popupX && mouseX < this.popupX + this.popupWidth
                && mouseY >= this.popupY && mouseY < this.popupY + this.popupRows * POPUP_ROW_HEIGHT) {
            this.selected = Math.min(this.firstVisible + (int) (mouseY - this.popupY) / POPUP_ROW_HEIGHT,
                    this.result.entries.size() - 1);
            accept();
            return true;
        }
        boolean handled = super.m_6375_(mouseX, mouseY, button);
        this.checkedCursor = -1;
        return handled;
    }

    private ScriptDiagnostic diagnostic() {
        ScriptDiagnostic runtime = ScriptDiagnostics.get(this.owner);
        return runtime != null && runtime.matches(getText()) ? runtime : this.staticDiagnostic;
    }

    /** Button-only lint. No writes to the container, executor, console or diagnostic NBT. */
    public void simpleCheck() {
        this.staticText = getText();
        this.staticDiagnostic = ScriptDiagnostics.checkStatic(this.owner, this.staticText, this.javascript ? "js" : "");
        this.checkStatus = !this.javascript || this.owner == null
                ? I18n.get("cnpcplus.script.check_skipped")
                : this.staticDiagnostic != null ? warning(this.staticDiagnostic)
                : !this.owner.scripts.isEmpty() ? I18n.get("cnpcplus.script.check_brackets_only")
                : I18n.get("cnpcplus.script.check_clear");
        this.marked = null;
    }

    private void markError() {
        if (this.staticText != null && !getText().equals(this.staticText)) {
            this.staticText = null;
            this.staticDiagnostic = null;
            this.checkStatus = "";
            this.marked = null;
        }
        ScriptDiagnostic runtime = ScriptDiagnostics.get(this.owner);
        TextContainer container = access().cnpcplus$container();
        if (container == this.marked && runtime == this.markedRuntime && this.staticDiagnostic == this.markedWarning) return;
        this.marked = container;
        this.markedRuntime = runtime;
        this.markedWarning = this.staticDiagnostic;
        container.makeup.clear();
        container.formatCodeText();
        this.status = this.checkStatus;
        this.statusColor = 0xFF202020;
        if (this.staticDiagnostic != null) {
            int[] range = ScriptDiagnostic.range(getText(), this.staticDiagnostic.line);
            if (range[0] >= 0) {
                int start = Math.min(range[1], range[0] + Math.max(0, this.staticDiagnostic.column - 1));
                int end = Math.min(range[1], start + this.staticDiagnostic.length);
                if (end > start) container.addMakeUp(start, end, 'e', 90);
            }
        }
        if (runtime == null) return;
        if (!runtime.matches(getText())) {
            this.status = I18n.get("cnpcplus.script.error_stale");
            this.statusColor = 0xFFFF6666;
            return;
        }
        this.statusColor = 0xFFFF6666;
        int[] range = ScriptDiagnostic.range(getText(), runtime.line);
        if (range[0] < 0 || runtime.line == 0) {
            this.status = I18n.get("cnpcplus.script.error_external");
            return;
        }
        this.status = I18n.get("cnpcplus.script.error_line", runtime.line);
        if (range[1] > range[0]) container.addMakeUp(range[0], range[1], 'c', 100);
        if (!this.jumped) {
            jumpToError();
            this.jumped = true;
        }
    }

    private void jumpToError() {
        ScriptDiagnostic diagnostic = diagnostic();
        if (diagnostic == null || !diagnostic.matches(getText())) return;
        int[] range = ScriptDiagnostic.range(getText(), diagnostic.line);
        if (range[0] < 0) return;
        IGuiTextAreaScriptAccess access = access();
        access.cnpcplus$cursor(range[0], false);
        TextContainer container = access.cnpcplus$container();
        try {
            for (int i = 0; i < container.lines.size(); i++) {
                Object row = container.lines.get(i);
                Field start = row.getClass().getDeclaredField("start");
                Field end = row.getClass().getDeclaredField("end");
                start.setAccessible(true);
                end.setAccessible(true);
                if (range[0] >= start.getInt(row) && range[0] < end.getInt(row)) {
                    access.cnpcplus$scroll(Math.max(0,
                            Math.min(i - 2, container.linesCount - container.visibleLines)));
                    break;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // The marker and F4 caret still work if CNPC changes its wrapped-row internals.
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.visible) return;
        markError();
        super.render(graphics, mouseX, mouseY);
        refresh(false);

        Font font = font();
        String help = I18n.get(this.javascript
                ? "cnpcplus.script.completion_help" : "cnpcplus.script.completion_js");
        String firstLine = this.status.isEmpty() ? help : this.status;
        int statusWidth = this.width;
        graphics.drawString(font, font.plainSubstrByWidth(firstLine, statusWidth),
                this.x, this.y + this.height + 3, this.status.isEmpty() ? 0xFF202020 : this.statusColor, false);
        String detail = this.hints.isEmpty() ? (this.status.isEmpty() ? "" : help) : this.hints.get(0).signature;
        graphics.drawString(font, font.plainSubstrByWidth(detail, statusWidth),
                this.x, this.y + this.height + 14, 0xFF202020, false);

        this.popupRows = 0;
        this.referenceHovered = false;
        if (this.active && this.result != null && !this.result.entries.isEmpty()) {
            drawPopup(graphics, mouseX, mouseY);
        }
        drawStatusTooltip(graphics, mouseX, mouseY);
    }

    private void drawStatusTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < this.x || mouseX >= this.x + this.width
                || mouseY < this.y + this.height || mouseY >= this.y + this.height + 24) return;
        List<String> lines = new ArrayList<>();
        ScriptDiagnostic diagnostic = diagnostic();
        if (diagnostic != null && mouseY < this.y + this.height + 12) {
            if (!this.status.isEmpty()) lines.add(this.status);
            lines.add(diagnostic == this.staticDiagnostic ? warning(diagnostic) : diagnostic.message);
            if (this.staticDiagnostic != null && diagnostic != this.staticDiagnostic) {
                lines.add(warning(this.staticDiagnostic));
            }
        } else {
            lines.add(I18n.get(this.javascript ? "cnpcplus.script.completion_help" : "cnpcplus.script.completion_js"));
            if (!this.checkStatus.isEmpty()) lines.add(this.checkStatus);
            for (ScriptApiCompletion.Entry hint : this.hints) {
                lines.add(hint.signature);
            }
        }
        if (!lines.isEmpty()) drawContainedTooltip(graphics, String.join("\n", lines), this.y + this.height - 4);
    }

    private static String warning(ScriptDiagnostic diagnostic) {
        return I18n.get(diagnostic.hintKey, diagnostic.line, diagnostic.subject);
    }

    private void drawPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        int maxRows = Math.max(1, Math.min(POPUP_ROWS, (this.height - 36) / POPUP_ROW_HEIGHT));
        this.firstVisible = Math.max(0, Math.min(this.firstVisible,
                Math.max(0, this.result.entries.size() - maxRows)));
        this.popupRows = Math.min(maxRows, this.result.entries.size() - this.firstVisible);
        this.popupWidth = Math.min(this.width - 4, 330);
        this.popupX = this.x + this.width - this.popupWidth - 2;
        this.popupY = this.y + this.height - this.popupRows * POPUP_ROW_HEIGHT - 2;
        graphics.fill(this.popupX - 1, this.popupY - 1, this.popupX + this.popupWidth + 1,
                this.popupY + this.popupRows * POPUP_ROW_HEIGHT + 1, 0xFF547689);
        graphics.fill(this.popupX, this.popupY, this.popupX + this.popupWidth,
                this.popupY + this.popupRows * POPUP_ROW_HEIGHT, 0xFA15222C);
        for (int row = 0; row < this.popupRows; row++) {
            int index = this.firstVisible + row;
            if (index == this.selected) {
                graphics.fill(this.popupX, this.popupY + row * POPUP_ROW_HEIGHT,
                        this.popupX + this.popupWidth, this.popupY + (row + 1) * POPUP_ROW_HEIGHT, 0xFF304B5B);
            }
            ScriptApiCompletion.Entry entry = this.result.entries.get(index);
            graphics.drawString(font(), font().plainSubstrByWidth(entry.signature, this.popupWidth - 6),
                    this.popupX + 3, this.popupY + row * POPUP_ROW_HEIGHT + 2,
                    entry.method ? 0xFFFFE1A0 : 0xFF88DEFF, false);
        }
        if (mouseX >= this.popupX && mouseX < this.popupX + this.popupWidth
                && mouseY >= this.popupY && mouseY < this.popupY + this.popupRows * POPUP_ROW_HEIGHT) {
            int index = this.firstVisible + (mouseY - this.popupY) / POPUP_ROW_HEIGHT;
            drawContainedTooltip(graphics, this.result.entries.get(index).signature, this.popupY - 3);
        }
    }

    /** Keep reference text above the footer; external tooltip positioners must not cover controls. */
    private void drawContainedTooltip(GuiGraphics graphics, String text, int bottom) {
        this.referenceHovered = true;
        if (!text.equals(this.referenceText)) { this.referenceText = text; this.referenceScroll = 0; }
        List<FormattedCharSequence> lines = font().split(Component.literal(text), this.width - 12);
        this.referenceRows = Math.max(1, Math.min(lines.size(), (bottom - Math.max(4, this.y) - 8) / font().lineHeight));
        this.referenceScroll = Math.min(this.referenceScroll, Math.max(0, lines.size() - this.referenceRows));
        int boxWidth = 0;
        for (var line : lines) boxWidth = Math.max(boxWidth, font().width(line));
        int h = this.referenceRows * font().lineHeight + 8;
        int yy = bottom - h;
        int xx = Math.max(4, Math.min(this.x + this.width - boxWidth - 8,
                Minecraft.getInstance().getWindow().getGuiScaledWidth() - boxWidth - 12));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(xx, yy, xx + boxWidth + 8, yy + h, 0xFF181818);
        for (int i = 0; i < this.referenceRows; i++)
            graphics.drawString(font(), lines.get(this.referenceScroll + i), xx + 4, yy + 4 + i * font().lineHeight, 0xFFF0F0F0, false);
        if (lines.size() > this.referenceRows)
            graphics.fill(xx + boxWidth + 6, yy + 4, xx + boxWidth + 8, yy + h - 4, 0xFFAAAAAA);
        graphics.pose().popPose();
    }
}
