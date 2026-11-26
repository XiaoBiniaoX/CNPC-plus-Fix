package top.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import bin.cnpcplus.script.ScriptStaticCheck;

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

    public enum AnalysisState { COMPLETE, UNSUPPORTED, INCOMPLETE }
    /** Completion state is independent of finding severity. COMPLETE may contain ERROR findings. */
    public record AnalysisResult(AnalysisState state, List<Finding> findings, ApiText statusMessage) {
        public AnalysisResult {
            java.util.Objects.requireNonNull(state); findings = List.copyOf(findings);
            java.util.Objects.requireNonNull(statusMessage);
        }
    }
    @FunctionalInterface public interface DetailedChecker { AnalysisResult check(Source source); }
    private record Registration(Set<String> languages, Checker checker, DetailedChecker detailed) {}

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static final ConcurrentHashMap<String, Registration> CHECKERS = new ConcurrentHashMap<>();
    private static final Set<String> GLOBALS = ConcurrentHashMap.newKeySet();
    private ScriptCheckApi() {}

    /** Register a namespaced checker; a duplicate id replaces the old implementation. */
    public static void register(String id, Checker checker) {
        if (id == null || !ID.matcher(id).matches() || checker == null) throw new IllegalArgumentException("id/checker");
        CHECKERS.put(id, new Registration(Set.of(), checker, null));
    }
    /** A language-specific legacy checker establishes COMPLETE on return and INCOMPLETE on failure. */
    public static void register(String id, Set<String> languageIds, Checker checker) {
        if (id == null || !ID.matcher(id).matches() || checker == null || languageIds == null || languageIds.isEmpty())
            throw new IllegalArgumentException("id/languageIds/checker");
        CHECKERS.put(id, new Registration(Set.copyOf(languageIds), checker, null));
    }
    /** Declares pure language checks, including explicit unsupported/incomplete reasons. Same id replaces. */
    public static void registerDetailed(String id, Set<String> languageIds, DetailedChecker checker) {
        if (id == null || !ID.matcher(id).matches() || checker == null || languageIds == null || languageIds.isEmpty())
            throw new IllegalArgumentException("id/languageIds/checker");
        CHECKERS.put(id, new Registration(Set.copyOf(languageIds), null, checker));
    }
    public static void unregister(String id) {
        if (id != null) CHECKERS.remove(id);
    }
    /** Declares a binding for analysis only; does not bind a value into the script engine. */
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
    public static Set<String> registeredGlobals() { return Set.copyOf(GLOBALS); }

    /** Runs addon rules only, in id order, isolating each addon's failures. */
    public static List<Finding> check(Source source) {
        if (source == null) throw new IllegalArgumentException("source");
        return collect(new Source(source.text(), ScriptLanguageApi.engineName(source.language()), source.globals()),
                false, false, Set.of()).findings();
    }

    private static AnalysisResult collect(Source source, boolean builtin, boolean references, Set<String> hooks) {
        if (source == null) throw new IllegalArgumentException("source");
        var findings = new ArrayList<Finding>();
        Reporter reporter = (severity, offset, length, key, subject) -> {
            if (severity == null || key == null || key.isBlank()) return;
            int start = Math.max(0, Math.min(source.text().length(), offset));
            findings.add(new Finding(severity, start, Math.max(0, Math.min(source.text().length() - start, length)),
                    key, subject == null ? "" : subject));
        };
        boolean supported = builtin, incomplete = false;
        ApiText status = ApiText.literal("");
        if (builtin) {
            try {
                for (var item : ScriptStaticCheck.checkAll(source.text(), source.globals(), references, hooks)) {
                    int start = ScriptStaticCheck.offset(source.text(), item.line, item.column);
                    reporter.report(Severity.valueOf(item.severity.name()), start, item.length, item.hintKey, item.subject);
                }
            } catch (RuntimeException | LinkageError failure) {
                incomplete = true; status = ApiText.literal("Built-in checker: " + failure);
                org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Built-in script check failed", failure);
            }
        }
        String language = ScriptLanguageApi.resolve(source.language());
        for (var entry : CHECKERS.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            var registration = entry.getValue();
            boolean declared = !registration.languages.isEmpty();
            if (declared && registration.languages.stream().map(ScriptLanguageApi::resolve).noneMatch(language::equals)) continue;
            try {
                if (registration.detailed != null) {
                    var result = java.util.Objects.requireNonNull(registration.detailed.check(source));
                    for (var item : result.findings()) reporter.report(item.severity(), item.offset(), item.length(), item.translationKey(), item.subject());
                    if (result.state() != AnalysisState.UNSUPPORTED) supported = true;
                    if (result.state() == AnalysisState.INCOMPLETE) {
                        incomplete = true;
                        status = result.statusMessage().value().isEmpty()
                                ? ApiText.translatable("cnpcplus.script.check_backend_incomplete", entry.getKey()) : result.statusMessage();
                    }
                } else {
                    registration.checker.check(source, reporter);
                    if (declared) supported = true;
                }
            }
            catch (RuntimeException | LinkageError failure) {
                reporter.report(Severity.WARN, 0, 0, "cnpcplus.script.addon_check_failed", entry.getKey());
                org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Script checker {} failed", entry.getKey(), failure);
                if (declared) { incomplete = true; status = ApiText.literal(entry.getKey() + ": " + failure); }
            }
        }
        var state = incomplete ? AnalysisState.INCOMPLETE : supported ? AnalysisState.COMPLETE : AnalysisState.UNSUPPORTED;
        return new AnalysisResult(state, findings, status);
    }

    /** Combined editor/headless entry point. Never evaluates the source or invokes script hooks. */
    public static List<Finding> analyze(Source source, boolean references, Set<String> hooks) {
        return analyzeDetailed(source, references, hooks).findings();
    }

    /** Pure analysis; generic legacy rules add findings but cannot alone establish language support. */
    public static AnalysisResult analyzeDetailed(Source source, boolean references, Set<String> hooks) {
        if (source == null) throw new IllegalArgumentException("source");
        Set<String> globals = new HashSet<>(source.globals());
        globals.addAll(registeredGlobals());
        Source input = new Source(source.text(), ScriptLanguageApi.engineName(source.language()), globals);
        var result = collect(input, ScriptCompletionApi.usesBuiltinChecks(input.language()), references, hooks);
        var findings = new ArrayList<>(result.findings());
        findings.sort(Comparator.comparingInt((Finding item) -> item.severity().ordinal()).thenComparingInt(Finding::offset));
        return new AnalysisResult(result.state(), findings, result.statusMessage());
    }
}
