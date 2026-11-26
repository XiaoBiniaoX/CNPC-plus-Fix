package top.cnpcplus.api.script;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Server-safe extension point for script globals and non-executing self-check rules. */
public final class ScriptCheckApi {
    public enum Severity { ERROR, WARN, INFO }
    public enum CheckState { CHECKED, SKIPPED, FAILED, LEGACY_UNDECLARED }
    public record Source(String text, String language, Set<String> globals) {
        public Source {
            text = text == null ? "" : text;
            language = language == null ? "" : language;
            globals = globals == null ? Set.of() : Set.copyOf(globals);
        }
    }
    public record Finding(Severity severity, int offset, int length, String translationKey, String subject) {}
    public record CheckRun(String providerId, CheckState state, List<Finding> findings, String messageKey) {
        public CheckRun {
            findings = List.copyOf(findings);
            messageKey = messageKey == null ? "" : messageKey;
        }
    }

    @FunctionalInterface
    public interface Reporter {
        void report(Severity severity, int offset, int length, String translationKey, String subject);
    }

    @FunctionalInterface
    public interface Checker {
        void check(Source source, Reporter reporter);
    }

    @FunctionalInterface
    public interface StatusChecker {
        CheckState check(Source source, Reporter reporter);
    }

    private record Provider(Checker legacy, StatusChecker status) {}

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static final ConcurrentHashMap<String, Provider> CHECKERS = new ConcurrentHashMap<>();
    private static final Set<String> GLOBALS = ConcurrentHashMap.newKeySet();

    private ScriptCheckApi() {}

    /** Register a namespaced checker; a duplicate id replaces the old implementation. */
    public static void register(String id, Checker checker) {
        if (id == null || !ID.matcher(id).matches() || checker == null) throw new IllegalArgumentException("id/checker");
        CHECKERS.put(id, new Provider(checker, null));
    }

    /** Register a checker that declares whether it actually completed this source. Same-ID registration replaces. */
    public static void registerStatus(String id, Set<String> languages, StatusChecker checker) {
        if (id == null || !ID.matcher(id).matches() || checker == null || languages == null || languages.isEmpty())
            throw new IllegalArgumentException("id/languages/checker");
        Set<String> accepted = languages.stream()
                .map(value -> value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT))
                .filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (accepted.isEmpty()) throw new IllegalArgumentException("languages");
        CHECKERS.put(id, new Provider((source, reporter) -> {
            if (!accepted.contains(source.language().strip().toLowerCase(java.util.Locale.ROOT))) return;
            checker.check(source, reporter);
        }, (source, reporter) -> {
            if (!accepted.contains(source.language().strip().toLowerCase(java.util.Locale.ROOT))) return CheckState.SKIPPED;
            return checker.check(source, reporter);
        }));
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
        return checkWithStatus(source).stream().flatMap(run -> run.findings().stream()).toList();
    }

    /** Run each registered provider once and retain execution status separately from its diagnostics. */
    public static List<CheckRun> checkWithStatus(Source source) {
        if (source == null) throw new IllegalArgumentException("source");
        var runs = new java.util.ArrayList<CheckRun>();
        CHECKERS.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            var findings = new java.util.ArrayList<Finding>();
            Reporter reporter = (severity, offset, length, key, subject) -> {
                if (severity == null || key == null || key.isBlank()) return;
                int start = Math.max(0, Math.min(source.text().length(), offset));
                int end = (int) Math.min(source.text().length(), Math.max((long) start, (long) offset + Math.max(0, length)));
                findings.add(new Finding(severity, start, end - start, key, subject == null ? "" : subject));
            };
            CheckState state = entry.getValue().status == null ? CheckState.LEGACY_UNDECLARED : null;
            String messageKey = "";
            try {
                Provider provider = entry.getValue();
                if (provider.status == null) provider.legacy.check(source, reporter);
                else state = provider.status.check(source, reporter);
                if (state == null) {
                    state = CheckState.FAILED;
                    messageKey = "cnpcplus.script.addon_check_failed";
                }
            } catch (RuntimeException | LinkageError failure) {
                state = CheckState.FAILED;
                messageKey = "cnpcplus.script.addon_check_failed";
                reporter.report(Severity.WARN, 0, 0, messageKey, entry.getKey());
            }
            if (state == CheckState.FAILED) {
                messageKey = "cnpcplus.script.addon_check_failed";
                if (findings.stream().noneMatch(finding -> finding.translationKey().equals("cnpcplus.script.addon_check_failed"))) {
                    reporter.report(Severity.WARN, 0, 0, messageKey, entry.getKey());
                }
            }
            runs.add(new CheckRun(entry.getKey(), state, findings, messageKey));
        });
        return List.copyOf(runs);
    }
}
