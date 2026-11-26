package top.cnpcplus.api.script;

import java.util.*;
import java.util.function.Consumer;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import bin.cnpcplus.script.ScriptLanguageRuntime;

/** Process-local JSR-223 registration. Register in the addon mod constructor on both physical sides, before common setup. */
public final class ScriptLanguageApi {
    /** Stable namespaced id; extension includes its leading dot and may contain multiple dots. */
    public record LanguageDescriptor(String id, String engineName, Set<String> aliases, String extension,
                                     ApiText displayName, ApiText description) {
        public LanguageDescriptor {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_.-]+")) throw new IllegalArgumentException("language id");
            if (engineName == null || engineName.isBlank()) throw new IllegalArgumentException("engineName");
            engineName = engineName.strip();
            aliases = Set.copyOf(aliases);
            if (aliases.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("aliases");
            if (extension == null || !extension.matches("\\.[a-zA-Z0-9_.-]+")) throw new IllegalArgumentException("extension");
            extension = extension.toLowerCase(Locale.ROOT);
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(description, "description");
        }
    }
    private ScriptLanguageApi() {}

    /**
     * Register in the addon mod constructor on both physical sides, before the first controller.
     * Creates no engine. Same id replaces during startup; other ownership conflicts throw
     * IllegalArgumentException, and late registration throws IllegalStateException.
     * Each factory result must be independently owned; onClose is its only host disposal path.
     */
    public static void register(LanguageDescriptor descriptor, ScriptEngineFactory factory, Consumer<ScriptEngine> onClose) {
        ScriptLanguageRuntime.register(Objects.requireNonNull(descriptor), Objects.requireNonNull(factory), Objects.requireNonNull(onClose));
    }
    /** Thread-safe, case-insensitive descriptor lookup; never creates an interpreter. */
    public static Optional<LanguageDescriptor> find(String idOrAlias) { return ScriptLanguageRuntime.find(idOrAlias); }
    /** Immutable descriptor snapshot sorted by stable id. */
    public static List<LanguageDescriptor> languages() { return ScriptLanguageRuntime.languages(); }
    /** Canonical id for registered languages, otherwise the unchanged legacy engine name. */
    public static String resolve(String idOrAlias) { return find(idOrAlias).map(LanguageDescriptor::id).orElse(idOrAlias == null ? "" : idOrAlias); }
    /** Engine label passed to legacy completion/check providers and the native factory lookup. */
    public static String engineName(String idOrAlias) { return find(idOrAlias).map(LanguageDescriptor::engineName).orElse(idOrAlias == null ? "" : idOrAlias); }
    /** Complete suffix, including the dot. Empty means the language is unavailable to this controller. */
    public static String extension(String idOrAlias) { return ScriptLanguageRuntime.extension(idOrAlias); }
}
