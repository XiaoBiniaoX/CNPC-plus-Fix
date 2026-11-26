package top.cnpcplus.api.script;

import java.util.Objects;
import java.util.Optional;
import noppes.npcs.controllers.ScriptContainer;
import bin.cnpcplus.script.ScriptPageState;
import top.cnpcplus.api.script.ScriptPageApi.ScriptSourceSnapshot;

/** Saved/synchronized runtime diagnostics. Reporting does not disable, execute or reinitialize a page. */
public final class ScriptDiagnosticApi {
    /** UTF-16 range. sourceId="main" selects the main editor; (-1,0) means position unknown. */
    public record RuntimeDiagnostic(String sourceId, int offset, int length, ApiText message) {
        public RuntimeDiagnostic {
            if (sourceId == null || sourceId.isBlank() || offset < -1 || length < 0 || offset == -1 && length != 0)
                throw new IllegalArgumentException("diagnostic source/range");
            Objects.requireNonNull(message, "message");
        }
    }
    private ScriptDiagnosticApi() {}
    /** Main/client game thread only. Rejects old or cross-page snapshots without replacing current diagnostics. */
    public static boolean reportRuntimeError(ScriptContainer page, ScriptSourceSnapshot source, RuntimeDiagnostic diagnostic) {
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(diagnostic, "diagnostic");
        var state = ScriptPageState.of(page);
        state.refresh();
        if (!source.belongsTo(page) || source.revision() != state.revision()
                || !source.mainText().equals(page.script) || !source.effectiveLanguage().equals(state.effectiveLanguage())) return false;
        if (diagnostic.sourceId().equals("main") && diagnostic.offset() >= 0
                && (diagnostic.offset() > page.script.length() || diagnostic.length() > page.script.length() - diagnostic.offset()))
            throw new IllegalArgumentException("diagnostic outside main source");
        state.reportDiagnostic(diagnostic);
        return true;
    }
    /** Main/client thread only. Returns a still-matching saved/runtime diagnostic, never runs source. */
    public static Optional<RuntimeDiagnostic> getRuntimeError(ScriptContainer page) {
        var state = ScriptPageState.of(page); state.refresh();
        return Optional.ofNullable(state.diagnostic());
    }
    /** Clears only the diagnostic. Use invalidateRuntime(EXPLICIT) to retry a failed executor. */
    public static void clearRuntimeError(ScriptContainer page) { ScriptPageState.of(page).diagnostic(null); }
}
