package top.cnpcplus.api.script;

import java.lang.ref.WeakReference;
import java.util.Objects;
import noppes.npcs.controllers.ScriptContainer;
import bin.cnpcplus.script.ScriptPageState;
import bin.cnpcplus.script.ScriptError;

/** Page operations on the game main thread, or on the client thread for an editor copy. Never starts an engine. */
public final class ScriptPageApi {
    public enum ResetReason { SOURCE_CHANGED, LINKS_CHANGED, LANGUAGE_CHANGED, RELOAD, OWNER_REMOVED, SHUTDOWN, EXPLICIT }

    /** Immutable, page-bound source snapshot. Revision is runtime-local and is not a saved version number. */
    public static final class ScriptSourceSnapshot {
        private final WeakReference<ScriptContainer> page;
        private final long revision;
        private final String mainText, sourceHash, effectiveLanguage;
        private ScriptSourceSnapshot(ScriptContainer page, long revision, String text, String language) {
            this.page = new WeakReference<>(page);
            this.revision = revision; mainText = text; sourceHash = ScriptError.hash(text); effectiveLanguage = language;
        }
        public long revision() { return revision; }
        public String mainText() { return mainText; }
        public String sourceHash() { return sourceHash; }
        public String effectiveLanguage() { return effectiveLanguage; }
        boolean belongsTo(ScriptContainer owner) { return page.get() == owner; }
    }
    private ScriptPageApi() {}
    /** Empty means inherit. Unknown explicit ids are returned unchanged. */
    public static String getLanguageOverride(ScriptContainer page) { return ScriptPageState.of(page).languageOverride(); }
    public static String getEffectiveLanguage(ScriptContainer page) { return ScriptPageState.of(page).effectiveLanguage(); }
    /** Empty inherits; a known alias is saved as its stable id. Source text is not modified. */
    public static void setLanguageOverride(ScriptContainer page, String idOrAlias) {
        ScriptPageState.of(page).setLanguage(Objects.requireNonNull(idOrAlias, "idOrAlias"));
    }
    /** Closes the old registered instance once and revokes its tasks. Does not evaluate or clear source. */
    public static void invalidateRuntime(ScriptContainer page, ResetReason reason) {
        ScriptPageState.of(page).invalidate(Objects.requireNonNull(reason));
    }
    /** Main/client thread only. Captures one page identity and current runtime revision, without evaluation. */
    public static ScriptSourceSnapshot snapshot(ScriptContainer page) {
        var state = ScriptPageState.of(page);
        state.refresh();
        return new ScriptSourceSnapshot(page, state.revision(), page.script, state.effectiveLanguage());
    }
}
