package bin.cnpcplus.script.client;

import bin.cnpcplus.mixin.script.IGuiTextAreaScriptAccess;
import bin.cnpcplus.api.script.ScriptCompletionApi;
import bin.cnpcplus.api.script.EditorContext;
import bin.cnpcplus.api.script.ScriptCheckApi;
import bin.cnpcplus.api.script.ScriptDiagnosticApi;
import bin.cnpcplus.api.script.ScriptMetadataApi;
import bin.cnpcplus.api.script.ScriptPage;
import bin.cnpcplus.api.script.SourceBundle;
import bin.cnpcplus.api.script.SourceUnit;
import bin.cnpcplus.script.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final String scriptLanguage;
    private String checkedText = null, dismissedText = null;
    private int checkedCursor = -1, dismissedCursor = -1, selected, popupX, popupY, popupWidth, popupRows;
    private long checkedRevision = -1;
    private ScriptCompletionApi.Result result;
    private List<String> hints = Collections.emptyList();
    private TextContainer marked;
    private boolean jumped;
    private String status = "";
    private String staticText, checkStatus = "";
    private ScriptDiagnostic staticDiagnostic, markedRuntime;
    private List<ScriptDiagnosticApi.Diagnostic> markedPublicRuntime = Collections.emptyList();
    private List<ScriptDiagnostic> staticDiagnostics = Collections.emptyList();
    private String observedDraft, checkText, checkedLanguageId;
    private long draftRevision, markedDraftRevision = -1L, markedProviderRevision = -1L;
    private long checkProviderRevision = -1L, checkProfileRevision = -1L;
    private CompletableFuture<ScriptCheckApi.CheckResult> checkFuture;

    public ScriptTextArea(int id, int x, int y, int width, int height, String text, ScriptContainer owner, String context, String language) {
        super(id, x, y, width, Math.max(24, height - 24), text);
        this.owner = owner; this.context = context;
        scriptLanguage = language == null ? "" : language;
        observedDraft = text == null ? "" : text;
    }
    private IGuiTextAreaScriptAccess access() { return (IGuiTextAreaScriptAccess) (Object) this; }
    private FontRenderer font() { return Minecraft.getMinecraft().fontRenderer; }
    public void resize(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = Math.max(24, height - 24);
        access().cnpcplus$container().lines.clear();
        access().cnpcplus$container().init(IGuiTextAreaScriptAccess.cnpcplus$font(), width, this.height);
        access().cnpcplus$scroll(Math.max(0, Math.min(access().cnpcplus$scroll(), access().cnpcplus$container().linesCount - access().cnpcplus$container().visibleLines)));
        clicked = clickScrolling = false;
    }
    public boolean hasPopup() { return active && result != null && !result.suggestions().isEmpty(); }

    private void refresh(boolean force) {
        int cursor = access().cnpcplus$cursor();
        long revision = ScriptCompletionApi.revision();
        if (!force && revision == checkedRevision && cursor == checkedCursor && getText().equals(checkedText)) return;
        checkedRevision = revision; checkedCursor = cursor; checkedText = getText(); selected = 0;
        result = ScriptCompletionApi.complete(new ScriptCompletionApi.Request(
                checkedText, cursor, scriptLanguage, context, ScriptHooks.names(owner, context), force));
        hints = result.signatures();
        if (!force && cursor == dismissedCursor && checkedText.equals(dismissedText)) result = null;
    }

    @Override public void keyTyped(char c, int key) {
        if (!active || !isEnabled()) { super.keyTyped(c, key); return; }
        if (key == Keyboard.KEY_F4) { jumpToError(); return; }
        if (ScriptCompletionApi.supports(scriptLanguage) && key == Keyboard.KEY_SPACE && GuiScreen.isCtrlKeyDown()) { refresh(true); return; }
        refresh(false);
        if (result != null && !result.suggestions().isEmpty()) {
            if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
                selected = Math.floorMod(selected + (key == Keyboard.KEY_UP ? -1 : 1), result.suggestions().size()); return;
            }
            if (key == Keyboard.KEY_TAB && !GuiScreen.isShiftKeyDown()) { accept(); return; }
            if (key == Keyboard.KEY_ESCAPE) { dismiss(); return; }
        }
        super.keyTyped(c, key);
        refresh(false);
    }
    private void dismiss() { dismissedCursor = access().cnpcplus$cursor(); dismissedText = getText(); result = null; }
    private void accept() {
        if (result == null || result.suggestions().isEmpty()) return;
        ScriptCompletionApi.Suggestion entry = result.suggestions().get(selected);
        IGuiTextAreaScriptAccess a = access();
        a.cnpcplus$cursor(entry.start(), false); a.cnpcplus$cursor(entry.end(), true);
        a.cnpcplus$insert(entry.insertText());
        a.cnpcplus$cursor(entry.start() + entry.caret(), false);
        clicked = false; checkedCursor = -1; refresh(false); dismiss();
    }

    @Override public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 && result != null && popupRows > 0 && mouseX >= popupX && mouseX < popupX + popupWidth
                && mouseY >= popupY && mouseY < popupY + popupRows * 12) {
            selected = Math.max(0, selected - selected % 6) + (mouseY - popupY) / 12;
            selected = Math.min(selected, result.suggestions().size() - 1); accept(); return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        checkedCursor = -1; return handled;
    }

    private ScriptDiagnostic diagnostic() {
        ScriptDiagnostic runtime = runtimeDiagnostic();
        return runtime == null ? staticDiagnostic : runtime;
    }
    private List<ScriptDiagnosticApi.Diagnostic> publicRuntimeDiagnostics() {
        List<ScriptDiagnosticApi.Diagnostic> result = new ArrayList<>();
        String sourceId = ((ScriptPage) (Object) owner).source().main().sourceId();
        String hash = ScriptDiagnosticApi.sourceHash(getText());
        for (ScriptDiagnosticApi.Diagnostic diagnostic : allPublicRuntimeDiagnostics())
            if (diagnostic.sourceId().equals(sourceId) && diagnostic.sourceHash().equals(hash)) result.add(diagnostic);
        return result;
    }
    private List<ScriptDiagnosticApi.Diagnostic> allPublicRuntimeDiagnostics() {
        ScriptPage page = (ScriptPage) (Object) owner;
        List<ScriptDiagnosticApi.Diagnostic> result = new ArrayList<>();
        for (ScriptDiagnosticApi.Diagnostic diagnostic : ScriptDiagnosticApi.diagnostics(page))
            result.add(diagnostic);
        return result;
    }
    private ScriptDiagnosticApi.Diagnostic preferredPublicRuntimeDiagnostic() {
        List<ScriptDiagnosticApi.Diagnostic> values = publicRuntimeDiagnostics();
        for (ScriptDiagnosticApi.Diagnostic value : values) if (value.severity() == ScriptDiagnosticApi.Severity.ERROR) return value;
        for (ScriptDiagnosticApi.Diagnostic value : values) if (value.severity() == ScriptDiagnosticApi.Severity.WARN) return value;
        return values.isEmpty() ? null : values.get(0);
    }
    private ScriptDiagnostic runtimeDiagnostic() {
        ScriptDiagnostic nativeDiagnostic = ScriptDiagnostics.get(owner);
        if (nativeDiagnostic != null && nativeDiagnostic.matches(getText())) return nativeDiagnostic;
        ScriptDiagnosticApi.Diagnostic diagnostic = preferredPublicRuntimeDiagnostic();
        return diagnostic == null ? null : toEditorDiagnostic(diagnostic);
    }
    public void simpleCheck() {
        staticText = getText();
        long revision = currentDraftRevision();
        ScriptPage page = (ScriptPage) (Object) owner;
        SourceBundle committed = page.source();
        SourceBundle draft = new SourceBundle(new SourceUnit("page", "Script", staticText), committed.linked(), committed.revision());
        Set<String> knownGlobals = new HashSet<>(ScriptHooks.globals(owner));
        knownGlobals.addAll(ScriptHooks.names(owner, context));
        ScriptMetadataApi.MetadataContext metadata = new ScriptMetadataApi.MetadataContext(
                page.contextKind(), page.effectiveLanguageId(), knownGlobals);
        EditorContext editor = new EditorContext(page, draft, revision, page.effectiveLanguageId(), page.contextKind(), metadata);
        if (checkFuture != null) checkFuture.cancel(false);
        checkText = staticText; checkedLanguageId = editor.languageId();
        ScriptCheckApi.CheckRequest request = new ScriptCheckApi.CheckRequest(draft, checkedLanguageId,
                editor.contextKind(), metadata, revision);
        checkProviderRevision = request.registryRevision();
        checkProfileRevision = request.profileRevision();
        checkFuture = ScriptCheckApi.checkAsync(request);
        staticDiagnostics = Collections.emptyList(); staticDiagnostic = null;
        checkStatus = I18n.format("cnpcplus.script.check_running");
        marked = null; clicked = false; active = true; dismiss();
    }

    /** Applies one provider template as a single native text edit and restores the provider caret. */
    public boolean applyTemplate(ScriptMetadataApi.HookInfo hook, String indentation) {
        EditorContext context = editorContext();
        ScriptCompletionApi.TemplateResult result = ScriptCompletionApi.templateResult(context, hook, indentation);
        ScriptCompletionApi.Suggestion template = result.suggestion();
        if (template == null && !result.error().isEmpty()) status = I18n.format("cnpcplus.script.completion_provider_error", result.error());
        if (template == null) return false;
        IGuiTextAreaScriptAccess a = access();
        a.cnpcplus$cursor(template.start(), false); a.cnpcplus$cursor(template.end(), true);
        a.cnpcplus$insert(template.insertText());
        a.cnpcplus$cursor(template.start() + template.caret(), false);
        clicked = false; checkedCursor = -1; dismiss();
        return true;
    }
    private static ScriptDiagnostic preferredDiagnostic(List<ScriptDiagnostic> diagnostics) {
        for (ScriptDiagnostic d : diagnostics) if (d.severity == ScriptDiagnostic.Severity.ERROR) return d;
        for (ScriptDiagnostic d : diagnostics) if (d.severity == ScriptDiagnostic.Severity.WARN) return d;
        return diagnostics.get(0);
    }
    private void markError() {
        currentDraftRevision();
        if (staticText != null && !getText().equals(staticText)) {
            staticText = null; staticDiagnostic = null; staticDiagnostics = Collections.emptyList(); checkStatus = ""; marked = null;
            if (checkFuture != null) { checkFuture.cancel(false); checkFuture = null; }
        }
        applyCheckResult();
        List<ScriptDiagnosticApi.Diagnostic> publicRuntime = publicRuntimeDiagnostics();
        List<ScriptDiagnosticApi.Diagnostic> allPublicRuntime = allPublicRuntimeDiagnostics();
        ScriptDiagnosticApi.Diagnostic preferredPublicRuntime = preferredPublicRuntimeDiagnostic();
        ScriptDiagnostic nativeRuntime = ScriptDiagnostics.get(owner);
        ScriptDiagnostic runtime = nativeRuntime != null && nativeRuntime.matches(getText())
                ? nativeRuntime : preferredPublicRuntime == null ? null : toEditorDiagnostic(preferredPublicRuntime);
        TextContainer container = access().cnpcplus$container();
        long providerRevision = ScriptCompletionApi.revision();
        long currentRevision = currentDraftRevision();
        if (container == marked && (allPublicRuntime.isEmpty() ? runtime == markedRuntime : allPublicRuntime.equals(markedPublicRuntime))
                && providerRevision == markedProviderRevision
                && currentRevision == markedDraftRevision) return;
        marked = container; markedRuntime = allPublicRuntime.isEmpty() ? runtime : null; markedPublicRuntime = allPublicRuntime;
        markedProviderRevision = providerRevision; markedDraftRevision = currentRevision;
        container.makeup.clear(); container.formatCodeText(); status = checkStatus;
        EditorContext editor = editorContext();
        ScriptCompletionApi.ColoringResult colors = ScriptCompletionApi.color(editor);
        if (!colors.error().isEmpty()) status = I18n.format("cnpcplus.script.completion_provider_error", colors.error());
        for (ScriptCompletionApi.TextSpan span : colors.spans()) {
            char color = color(span.category());
            if (color != 0) container.addMakeUp(span.start(), span.end(), color, 20);
        }
        for (ScriptDiagnostic finding : staticDiagnostics) {
            if (finding.offset < 0 || finding.offset >= getText().length()) continue;
            int end = Math.min(getText().length(), finding.offset + Math.max(1, finding.length));
            if (end <= finding.offset) continue;
            char color = finding.severity == ScriptDiagnostic.Severity.ERROR ? 'c'
                    : finding.severity == ScriptDiagnostic.Severity.WARN ? 'e' : '9';
            container.addMakeUp(finding.offset, end, color, finding.severity == ScriptDiagnostic.Severity.ERROR ? 100 : 90);
        }
        for (ScriptDiagnosticApi.Diagnostic finding : publicRuntime) {
            if (finding.offset() < 0 || finding.offset() >= getText().length()) continue;
            int end = Math.min(getText().length(), finding.offset() + Math.max(1, finding.length()));
            if (end <= finding.offset()) continue;
            char color = finding.severity() == ScriptDiagnosticApi.Severity.ERROR ? 'c'
                    : finding.severity() == ScriptDiagnosticApi.Severity.WARN ? 'e' : '9';
            container.addMakeUp(finding.offset(), end, color, 110);
        }
        if (runtime == null) {
            if (!allPublicRuntimeDiagnostics().isEmpty()) status = I18n.format("cnpcplus.script.error_external");
            return;
        }
        if (!runtime.matches(getText())) { status = I18n.format("cnpcplus.script.error_stale"); return; }
        int start, end;
        if (runtime.offset >= 0) {
            start = runtime.offset; end = Math.min(getText().length(), start + runtime.length);
        } else {
            int[] range = ScriptDiagnostic.range(getText(), runtime.line);
            start = range[0]; end = range[1];
        }
        if (start < 0 || runtime.line == 0 && runtime.offset < 0) { status = I18n.format("cnpcplus.script.error_external"); return; }
        status = I18n.format("cnpcplus.script.error_line", runtime.line);
        if (end > start) container.addMakeUp(start, end, runtime.severity == ScriptDiagnostic.Severity.ERROR ? 'c'
                : runtime.severity == ScriptDiagnostic.Severity.WARN ? 'e' : '9', 100);
        if (!jumped) { jumpToError(); jumped = true; }
    }

    private void applyCheckResult() {
        if (checkFuture == null || !checkFuture.isDone()) return;
        CompletableFuture<ScriptCheckApi.CheckResult> future = checkFuture;
        checkFuture = null;
        if (future.isCancelled() || !getText().equals(checkText)
                || !Objects.equals(checkedLanguageId, ((ScriptPage)(Object)owner).effectiveLanguageId())
                || checkProviderRevision != ScriptCheckApi.registryRevision()
                || checkProfileRevision != ScriptCompletionApi.revision()) {
            checkStatus = ""; staticDiagnostics = Collections.emptyList(); staticDiagnostic = null; marked = null; return;
        }
        ScriptCheckApi.CheckResult result;
        try { result = future.getNow(null); }
        catch (java.util.concurrent.CompletionException failure) {
            checkStatus = I18n.format("cnpcplus.script.check_failed", failure.getCause() == null ? "FAILED" : failure.getCause().getClass().getSimpleName());
            return;
        }
        if (result == null || result.requestRevision() != draftRevision
                || result.registryRevision() != checkProviderRevision || result.profileRevision() != checkProfileRevision) {
            checkStatus = ""; staticDiagnostics = Collections.emptyList(); staticDiagnostic = null; marked = null; return;
        }
        List<ScriptDiagnostic> current = new ArrayList<>();
        SourceUnit main = new SourceUnit("page", "Script", checkText);
        for (ScriptCheckApi.Finding finding : result.findings()) {
            if (!main.sourceId().equals(finding.sourceId())) continue;
            ScriptDiagnostic.Severity severity = finding.severity() == ScriptCheckApi.Severity.ERROR ? ScriptDiagnostic.Severity.ERROR
                    : finding.severity() == ScriptCheckApi.Severity.INFO ? ScriptDiagnostic.Severity.INFO : ScriptDiagnostic.Severity.WARN;
            current.add(ScriptDiagnostic.at(checkText, finding.offset(), finding.length(), severity,
                    finding.translationKey(), finding.subject()));
        }
        staticDiagnostics = Collections.unmodifiableList(current);
        staticDiagnostic = current.isEmpty() ? null : preferredDiagnostic(current);
        if (result.status() == ScriptCheckApi.Status.UNSUPPORTED) checkStatus = I18n.format("cnpcplus.script.check_unsupported");
        else if (result.status() == ScriptCheckApi.Status.DEPENDENCY_MISSING) checkStatus = I18n.format("cnpcplus.script.check_dependency_missing");
        else if (result.status() == ScriptCheckApi.Status.TIMED_OUT) checkStatus = I18n.format("cnpcplus.script.check_timed_out");
        else if (result.status() == ScriptCheckApi.Status.FAILED) checkStatus = I18n.format("cnpcplus.script.check_failed", result.message().isEmpty() ? "FAILED" : result.message());
        else {
            int errors = 0, warnings = 0, info = 0;
            for (ScriptDiagnostic finding : current) {
                if (finding.severity == ScriptDiagnostic.Severity.ERROR) errors++;
                else if (finding.severity == ScriptDiagnostic.Severity.WARN) warnings++;
                else info++;
            }
            checkStatus = current.isEmpty() ? I18n.format("cnpcplus.script.check_clear")
                    : I18n.format("cnpcplus.script.check_summary", errors, warnings, info);
        }
        marked = null;
        if (staticDiagnostic != null) jumpToError();
    }

    private long currentDraftRevision() {
        String text = getText();
        if (!Objects.equals(text, observedDraft)) { observedDraft = text; draftRevision++; }
        return draftRevision;
    }

    private EditorContext editorContext() {
        long revision = currentDraftRevision();
        ScriptPage page = (ScriptPage) (Object) owner;
        SourceBundle committed = page.source();
        SourceBundle draft = new SourceBundle(new SourceUnit("page", "Script", getText()), committed.linked(), committed.revision());
        ScriptMetadataApi.MetadataContext metadata = ScriptMetadataApi.context(page, ScriptHooks.globals(owner));
        return new EditorContext(page, draft, revision, page.effectiveLanguageId(), page.contextKind(), metadata);
    }

    private static char color(ScriptCompletionApi.SyntaxCategory category) {
        switch (category) {
            case KEYWORD: return 'b'; case STRING: return 'a'; case NUMBER: return 'd'; case COMMENT: return '7';
            case FUNCTION: return 'e'; case TYPE: return '9'; case OPERATOR: return 'f'; default: return 0;
        }
    }
    private ScriptDiagnostic toEditorDiagnostic(ScriptDiagnosticApi.Diagnostic diagnostic) {
        ScriptDiagnostic.Severity severity = diagnostic.severity() == ScriptDiagnosticApi.Severity.ERROR ? ScriptDiagnostic.Severity.ERROR
                : diagnostic.severity() == ScriptDiagnosticApi.Severity.INFO ? ScriptDiagnostic.Severity.INFO : ScriptDiagnostic.Severity.WARN;
        String arguments = String.join(", ", diagnostic.arguments());
        if (diagnostic.offset() >= 0) {
            ScriptDiagnostic located = ScriptDiagnostic.at(getText(), diagnostic.offset(), diagnostic.length(), severity,
                    diagnostic.translationKey(), arguments);
            return new ScriptDiagnostic(located.line, located.column, diagnostic.offset(), diagnostic.sourceHash(),
                    diagnostic.message(), diagnostic.translationKey(), arguments, diagnostic.length(), severity);
        }
        return new ScriptDiagnostic(diagnostic.line(), diagnostic.column(), -1, diagnostic.sourceHash(),
                diagnostic.message(), diagnostic.translationKey(), arguments, 0, severity);
    }
    private void jumpToError() {
        ScriptDiagnostic d = diagnostic();
        if (d == null || !d.matches(getText())) return;
        int start;
        if (d.offset >= 0) start = Math.min(getText().length(), d.offset);
        else {
            int[] range = ScriptDiagnostic.range(getText(), d.line);
            if (range[0] < 0) return;
            start = range[0];
        }
        IGuiTextAreaScriptAccess a = access();
        int caret = d.offset >= 0 ? start : Math.min(getText().length(), start + Math.max(0, d.column - 1));
        a.cnpcplus$cursor(caret, false);
        TextContainer container = a.cnpcplus$container();
        // LineData is package-private. Read its actual wrapped-row start; never assume a fixed-width font.
        try {
            for (int i = 0; i < container.lines.size(); i++) {
                Object row = container.lines.get(i);
                Field startField = row.getClass().getDeclaredField("start"); startField.setAccessible(true);
                Field end = row.getClass().getDeclaredField("end"); end.setAccessible(true);
                if (caret >= startField.getInt(row) && caret < end.getInt(row)) {
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
        ScriptCompletionApi.Language profile = ScriptCompletionApi.language(scriptLanguage);
        String help = profile != null && !profile.helpText().isEmpty() ? profile.helpText()
                : profile != null && !profile.helpTranslationKey().isEmpty() ? I18n.format(profile.helpTranslationKey())
                : I18n.format(ScriptCompletionApi.supports(scriptLanguage)
                        ? "cnpcplus.script.completion_help" : "cnpcplus.script.completion_js");
        f.drawString(f.trimStringToWidth(status.isEmpty() ? help : status, width), x, y + height + 3, status.isEmpty() ? 0xA0BBC6 : 0xFF6666);
        String detail = result != null && !result.error().isEmpty()
                ? I18n.format("cnpcplus.script.completion_provider_error", result.error())
                : hints.isEmpty() ? "" : hints.get(0);
        f.drawString(f.trimStringToWidth(detail, width), x, y + height + 14, 0xFFDF88);
        popupRows = 0;
        if (active && result != null && !result.suggestions().isEmpty()) drawPopup(mouseX, mouseY);
        if (mouseX >= x && mouseX <= x + width && mouseY >= y + height && mouseY < y + height + 24) {
            List<String> lines = new ArrayList<>();
            ScriptDiagnostic d = diagnostic();
            List<ScriptDiagnosticApi.Diagnostic> publicDiagnostics = allPublicRuntimeDiagnostics();
            if ((d != null || !publicDiagnostics.isEmpty()) && mouseY < y + height + 12) {
                lines.add(status);
                int count = 0;
                for (ScriptDiagnostic finding : staticDiagnostics) {
                    if (count++ == 12) { lines.add(I18n.format("cnpcplus.script.more_findings")); break; }
                    String severity = I18n.format("cnpcplus.script.severity." + finding.severity.name().toLowerCase(Locale.ROOT));
                    String findingText = I18n.format(finding.warningKey, finding.token, finding.line);
                    lines.add(I18n.format("cnpcplus.script.finding", severity, finding.line, findingText));
                }
                ScriptDiagnostic runtime = ScriptDiagnostics.get(owner);
                if (runtime != null && runtime.matches(getText())) lines.addAll(f.listFormattedStringToWidth(runtime.message, 300));
                for (ScriptDiagnosticApi.Diagnostic finding : publicDiagnostics) {
                    String severity = I18n.format("cnpcplus.script.severity." + finding.severity().name().toLowerCase(Locale.ROOT));
                    lines.addAll(f.listFormattedStringToWidth(severity + " [" + finding.sourceId() + "]: " + finding.message(), 300));
                }
            }
            else lines.addAll(hints);
            if (!lines.isEmpty()) GuiUtils.drawHoveringText(lines, mouseX, mouseY, Minecraft.getMinecraft().currentScreen.width,
                    Minecraft.getMinecraft().currentScreen.height, 320, f);
        }
    }
    private void drawPopup(int mouseX, int mouseY) {
        int first = selected - selected % 6;
        popupRows = Math.min(6, result.suggestions().size() - first);
        popupWidth = Math.min(width - 4, 330);
        popupX = x + width - popupWidth - 2; popupY = y + height - popupRows * 12 - 2;
        drawRect(popupX - 1, popupY - 1, popupX + popupWidth + 1, popupY + popupRows * 12 + 1, 0xFF547689);
        drawRect(popupX, popupY, popupX + popupWidth, popupY + popupRows * 12, 0xFA15222C);
        for (int row = 0; row < popupRows; row++) {
            int index = first + row;
            if (index == selected) drawRect(popupX, popupY + row * 12, popupX + popupWidth, popupY + (row + 1) * 12, 0xFF304B5B);
            ScriptCompletionApi.Suggestion e = result.suggestions().get(index);
            font().drawString(font().trimStringToWidth(e.label(), popupWidth - 6), popupX + 3, popupY + row * 12 + 2, 0xFFE1A0);
        }
        if (mouseX >= popupX && mouseX < popupX + popupWidth && mouseY >= popupY && mouseY < popupY + popupRows * 12) {
            ScriptCompletionApi.Suggestion e = result.suggestions().get(first + (mouseY - popupY) / 12);
            GuiUtils.drawHoveringText(Collections.singletonList(e.detail()), mouseX, mouseY,
                    Minecraft.getMinecraft().currentScreen.width, Minecraft.getMinecraft().currentScreen.height, 360, font());
        }
    }
}
