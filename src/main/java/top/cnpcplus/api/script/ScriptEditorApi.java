package top.cnpcplus.api.script;

import java.util.*;
import top.cnpcplus.api.script.ScriptMetadataApi.HookDescriptor;

/** Text-only providers, registered on both sides at startup. Calls never require GUI or runtime objects. */
public final class ScriptEditorApi {
    public enum HighlightKind { KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION, GLOBAL, LITERAL, OPERATOR }
    public record HighlightSpan(int offset, int length, HighlightKind kind) {}
    public record EditorSource(String text, String effectiveLanguage, String contextId, List<HookDescriptor> hooks) {
        public EditorSource {
            Objects.requireNonNull(text); Objects.requireNonNull(effectiveLanguage); Objects.requireNonNull(contextId);
            hooks = List.copyOf(hooks);
        }
    }
    /** Pure text callbacks. Do not evaluate source, create runtimes or access a world; failures fall back with a log. */
    public interface LanguageEditorProvider {
        default List<HighlightSpan> highlight(EditorSource source) { return List.of(); }
        default String hookTemplate(HookDescriptor hook) { return ""; }
        default ApiText completionHelp() { return ApiText.literal(""); }
    }
    private static volatile Map<String, LanguageEditorProvider> providers = Map.of();
    private static volatile long revision;
    private ScriptEditorApi() {}
    /** Same stable language id replaces its provider. Does not register an interpreter. */
    public static synchronized void register(String languageId, LanguageEditorProvider provider) {
        if (languageId == null || !languageId.matches("[a-z0-9_.-]+:[a-z0-9_.-]+")) throw new IllegalArgumentException("languageId");
        var updated = new HashMap<>(providers); updated.put(languageId, Objects.requireNonNull(provider));
        providers = Map.copyOf(updated); revision++;
    }
    /** Removes editor callbacks only. Does not unregister a runtime language. */
    public static synchronized void unregister(String languageId) {
        var updated = new HashMap<>(providers); updated.remove(languageId); providers = Map.copyOf(updated); revision++;
    }
    public static long revision() { return revision; }
    public static boolean hasProvider(String language) { return providers.containsKey(ScriptLanguageApi.resolve(language)); }
    /** Empty optional requests default coloring after no provider or a provider failure. */
    public static Optional<List<HighlightSpan>> highlight(EditorSource source) {
        var provider = providers.get(ScriptLanguageApi.resolve(source.effectiveLanguage()));
        if (provider == null) return Optional.empty();
        try {
            var spans = List.copyOf(provider.highlight(source));
            int end = 0;
            for (var span : spans) {
                if (span.kind() == null || span.offset() < end || span.length() < 0
                        || span.offset() > source.text().length() || span.length() > source.text().length() - span.offset())
                    throw new IllegalArgumentException("unsorted/overlapping/out-of-source highlight");
                end = span.offset() + span.length();
            }
            return Optional.of(spans);
        } catch (RuntimeException | LinkageError failure) { failed(source.effectiveLanguage(), "highlight", failure); return Optional.empty(); }
    }
    /** Pure LF template; invalid/provider-failed templates return empty text and log provider ownership. */
    public static String hookTemplate(String language, HookDescriptor hook) {
        var provider = providers.get(ScriptLanguageApi.resolve(language));
        if (provider == null) return ScriptCompletionApi.usesBuiltinChecks(language) ? "function " + hook.name() + "(event) {\n\n}\n" : hook.name();
        try {
            String template = Objects.requireNonNull(provider.hookTemplate(hook));
            if (template.indexOf('\r') >= 0) throw new IllegalArgumentException("template must use LF");
            return template;
        } catch (RuntimeException | LinkageError failure) { failed(language, "template", failure); return ""; }
    }
    /** Pure help lookup; translation is deferred to the displaying client. */
    public static ApiText completionHelp(String language) {
        var provider = providers.get(ScriptLanguageApi.resolve(language));
        if (provider == null) return ApiText.literal("");
        try { return Objects.requireNonNull(provider.completionHelp()); }
        catch (RuntimeException | LinkageError failure) { failed(language, "help", failure); return ApiText.literal(""); }
    }
    private static void failed(String language, String operation, Throwable failure) {
        org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Editor provider {} failed: {}", ScriptLanguageApi.resolve(language), operation, failure);
    }
}
