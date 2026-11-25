package top.cnpcplus.api.script;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Server-safe extension point for script globals and non-executing self-check rules. */
public final class ScriptCheckApi {
    public enum Severity { ERROR, WARN, INFO }
    public record Source(String text, String language, Set<String> globals) {
        public Source {
            text = text == null ? "" : text;
            language = language == null ? "" : language;
            globals = globals == null ? Set.of() : Set.copyOf(globals);
        }
    }
    public record Finding(Severity severity, int offset, int length, String translationKey, String subject) {}

    @FunctionalInterface
    public interface Reporter {
        void report(Severity severity, int offset, int length, String translationKey, String subject);
    }

    @FunctionalInterface
    public interface Checker {
        void check(Source source, Reporter reporter);
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static final ConcurrentHashMap<String, Checker> CHECKERS = new ConcurrentHashMap<>();
    private static final Set<String> GLOBALS = ConcurrentHashMap.newKeySet();

    private ScriptCheckApi() {}

    /** Register a namespaced checker; a duplicate id replaces the old implementation. */
    public static void register(String id, Checker checker) {
        if (id == null || !ID.matcher(id).matches() || checker == null) throw new IllegalArgumentException("id/checker");
        CHECKERS.put(id, checker);
    }

    public static void unregister(String id) {
        if (id != null) CHECKERS.remove(id);
    }

    /** Add an engine binding so the core undeclared-name check can recognize it. */
    public static void registerGlobal(String name) {
        if (name == null || name.isBlank() || !Character.isJavaIdentifierStart(name.charAt(0)))
            throw new IllegalArgumentException("name");
        for (int i = 1; i < name.length(); i++) if (!Character.isJavaIdentifierPart(name.charAt(i)))
            throw new IllegalArgumentException("name");
        GLOBALS.add(name);
    }

    public static void unregisterGlobal(String name) {
        if (name != null) GLOBALS.remove(name);
    }

    public static Set<String> registeredGlobals() {
        return Set.copyOf(GLOBALS);
    }

    /** Called by the editor after built-in analysis. Each addon checker is isolated from the others. */
    public static List<Finding> check(Source source) {
        if (source == null) throw new IllegalArgumentException("source");
        var findings = new java.util.ArrayList<Finding>();
        Reporter reporter = (severity, offset, length, key, subject) -> {
            if (severity == null || key == null || key.isBlank()) return;
            findings.add(new Finding(severity, Math.max(0, offset), Math.max(0, length), key, subject == null ? "" : subject));
        };
        CHECKERS.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                entry.getValue().check(source, reporter);
            } catch (RuntimeException | LinkageError failure) {
                reporter.report(Severity.WARN, 0, 0, "cnpcplus.script.addon_check_failed", entry.getKey());
            }
        });
        return List.copyOf(findings);
    }
}
