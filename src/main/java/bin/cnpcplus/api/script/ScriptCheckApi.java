package bin.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import bin.cnpcplus.script.ScriptDiagnostic;

/** Server-safe extension point for script globals and non-executing diagnostics. */
public final class ScriptCheckApi {
    public enum Severity { ERROR, WARN, INFO }

    public static final class Source {
        private final String text, language, sourceId, sourceHash;
        private final long revision;
        private final Set<String> globals;
        public Source(String text, String language, Set<String> globals) {
            this(text, language, globals, "page", 0L);
        }
        public Source(String text, String language, Set<String> globals, String sourceId, long revision) {
            this.text = SourceUnit.normalizeText(text);
            this.language = language == null ? "" : language;
            this.sourceId = sourceId == null ? "page" : sourceId;
            this.revision = revision;
            this.sourceHash = ScriptDiagnostic.hash(this.text);
            this.globals = Collections.unmodifiableSet(new HashSet<>(globals == null
                    ? Collections.<String>emptySet() : globals));
        }
        public String text() { return text; }
        public String language() { return language; }
        public String sourceId() { return sourceId; }
        public String sourceHash() { return sourceHash; }
        public long revision() { return revision; }
        public Set<String> globals() { return globals; }
    }

    public static final class Finding {
        private final Severity severity;
        private final int offset, length;
        private final String translationKey, subject, sourceId, sourceHash;
        private final long revision;
        Finding(Severity severity, int offset, int length, String translationKey, String subject,
                String sourceId, String sourceHash, long revision) {
            this.severity = severity; this.offset = offset; this.length = length;
            this.translationKey = translationKey; this.subject = subject;
            this.sourceId = sourceId; this.sourceHash = sourceHash; this.revision = revision;
        }
        public Severity severity() { return severity; }
        public int offset() { return offset; }
        public int length() { return length; }
        public String translationKey() { return translationKey; }
        public String subject() { return subject; }
        public String sourceId() { return sourceId; }
        public String sourceHash() { return sourceHash; }
        public long revision() { return revision; }
    }

    public enum Status { SUCCESS, UNSUPPORTED, DEPENDENCY_MISSING, TIMED_OUT, FAILED }

    public interface LanguageChecker { Status check(Source source, Reporter reporter); }

    public static final class CheckRequest {
        private final SourceBundle source;
        private final String languageId, contextKind;
        private final String resolvedLanguageId;
        private final ScriptMetadataApi.MetadataContext metadata;
        private final long requestRevision;
        private final long registryRevision;
        private final long profileRevision;
        private final LanguageChecker primaryChecker;
        private final boolean builtinChecker;
        private final Map<String, Checker> addonCheckers;
        private final Set<String> knownGlobals;

        public CheckRequest(SourceBundle source, String languageId, String contextKind,
                ScriptMetadataApi.MetadataContext metadata, long requestRevision) {
            if (source == null || metadata == null) throw new IllegalArgumentException("source/metadata");
            this.source = source; this.languageId = languageId == null ? "" : languageId;
            this.resolvedLanguageId = ScriptHostApi.language(this.languageId).map(LanguageDescriptor::id).orElse(this.languageId);
            this.contextKind = contextKind == null ? ScriptMetadataApi.UNKNOWN : contextKind;
            this.metadata = metadata; this.requestRevision = requestRevision;
            Set<String> globals = new HashSet<>(metadata.declaredGlobals());
            for (ScriptMetadataApi.GlobalInfo global : ScriptMetadataApi.globals(metadata)) globals.add(global.name());
            ScriptCompletionApi.CheckProfile profile = ScriptCompletionApi.checkProfile(this.languageId);
            synchronized (ScriptCheckApi.class) {
                this.registryRevision = ScriptCheckApi.registryRevision;
                this.profileRevision = profile.revision();
                this.primaryChecker = LANGUAGE_CHECKERS.get(this.resolvedLanguageId);
                this.builtinChecker = primaryChecker == null && profile.builtinChecks();
                this.addonCheckers = Collections.unmodifiableMap(new TreeMap<>(CHECKERS));
                globals.addAll(GLOBALS);
            }
            this.knownGlobals = Collections.unmodifiableSet(globals);
        }
        public SourceBundle source() { return source; }
        public String languageId() { return languageId; }
        public String resolvedLanguageId() { return resolvedLanguageId; }
        public String contextKind() { return contextKind; }
        public ScriptMetadataApi.MetadataContext metadata() { return metadata; }
        public long requestRevision() { return requestRevision; }
        public long registryRevision() { return registryRevision; }
        public long profileRevision() { return profileRevision; }
    }

    public static final class CheckResult {
        private final Status status;
        private final List<Finding> findings;
        private final long requestRevision;
        private final String mainSourceId, message;
        private final long registryRevision;
        private final long profileRevision;
        CheckResult(Status status, List<Finding> findings, long requestRevision, String mainSourceId,
                String message, long registryRevision, long profileRevision) {
            this.status = status; this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
            this.requestRevision = requestRevision; this.mainSourceId = mainSourceId;
            this.message = message == null ? "" : message;
            this.registryRevision = registryRevision;
            this.profileRevision = profileRevision;
        }
        public Status status() { return status; }
        public List<Finding> findings() { return findings; }
        public long requestRevision() { return requestRevision; }
        public String mainSourceId() { return mainSourceId; }
        public String message() { return message; }
        public long registryRevision() { return registryRevision; }
        public long profileRevision() { return profileRevision; }
    }

    public interface Reporter {
        void report(Severity severity, int offset, int length, String translationKey, String subject);
    }

    public interface Checker {
        void check(Source source, Reporter reporter);
    }

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static final ConcurrentHashMap<String, Checker> CHECKERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LanguageChecker> LANGUAGE_CHECKERS = new ConcurrentHashMap<>();
    private static final Set<String> GLOBALS = ConcurrentHashMap.newKeySet();
    private static long registryRevision;

    private ScriptCheckApi() {}

    /** Duplicate namespaced IDs replace the previous checker. */
    public static synchronized void register(String id, Checker checker) {
        if (id == null || !NAMESPACE.matcher(id).matches() || checker == null)
            throw new IllegalArgumentException("id/checker");
        CHECKERS.put(id, checker);
        registryRevision++;
    }

    public static synchronized void unregister(String id) { if (id != null && CHECKERS.remove(id) != null) registryRevision++; }

    /** Register the primary non-executing checker for a stable language ID. */
    public static synchronized void registerLanguageChecker(String languageId, LanguageChecker checker) {
        if (languageId == null || !languageId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || checker == null)
            throw new IllegalArgumentException("languageId/checker");
        LANGUAGE_CHECKERS.put(languageId, checker);
        registryRevision++;
    }

    public static synchronized void unregisterLanguageChecker(String languageId) {
        if (languageId != null && LANGUAGE_CHECKERS.remove(languageId) != null) registryRevision++;
    }

    /** Declare an addon-provided engine global to the built-in undeclared-name check. */
    public static synchronized void registerGlobal(String name) {
        if (!identifier(name)) throw new IllegalArgumentException("name");
        if (GLOBALS.add(name)) registryRevision++;
    }

    public static synchronized void unregisterGlobal(String name) { if (name != null && GLOBALS.remove(name)) registryRevision++; }

    public static synchronized long registryRevision() { return registryRevision; }

    public static synchronized Set<String> registeredGlobals() {
        return Collections.unmodifiableSet(new HashSet<>(GLOBALS));
    }

    public static List<Finding> check(Source source) {
        if (source == null) throw new IllegalArgumentException("source");
        final List<Finding> findings = new ArrayList<>();
        Reporter reporter = (severity, offset, length, key, subject) -> {
            if (severity == null || key == null || key.isEmpty()) return;
            int safeOffset = offset < 0 ? -1 : Math.min(offset, source.text().length());
            int safeLength = safeOffset < 0 ? 0 : Math.max(0, Math.min(length, source.text().length() - safeOffset));
            findings.add(new Finding(severity, safeOffset, safeLength, key,
                    subject == null ? "" : subject, source.sourceId(), source.sourceHash(), source.revision()));
        };
        Map<String, Checker> ordered = new TreeMap<>(CHECKERS);
        for (Map.Entry<String, Checker> entry : ordered.entrySet()) {
            try {
                entry.getValue().check(source, reporter);
            } catch (RuntimeException | LinkageError failure) {
                reporter.report(Severity.WARN, 0, 0, "cnpcplus.script.addon_check_failed", entry.getKey());
            }
        }
        return findings;
    }

    /** Synchronous unified check. No page runtime, event, entity or world object is accepted. */
    public static CheckResult check(CheckRequest request) {
        if (request == null) throw new IllegalArgumentException("request");
        List<Finding> findings = new ArrayList<>();
        Status status = Status.SUCCESS;
        boolean supported = false;
        LanguageChecker primary = request.primaryChecker;
        boolean builtin = request.builtinChecker;
        Set<String> knownGlobals = request.knownGlobals;
        Map<String, Checker> addonCheckers = request.addonCheckers;
        for (SourceUnit unit : units(request.source())) {
            Source source = new Source(unit.text(), request.languageId(), knownGlobals, unit.sourceId(), request.source().revision());
            if (primary != null) {
                supported = true;
                try { status = merge(status, normalize(primary.check(source, reporter(findings, source)))); }
                catch (RuntimeException | LinkageError failure) { status = merge(status, Status.FAILED); }
            } else if (builtin) {
                supported = true;
                try {
                    for (ScriptDiagnostic diagnostic : bin.cnpcplus.script.ScriptStaticCheck.checkBuiltinAll(
                            source.text(), request.languageId(), source.globals(), true))
                        findings.add(fromDiagnostic(diagnostic, source));
                } catch (RuntimeException | LinkageError failure) { status = merge(status, Status.FAILED); }
            }
            for (Map.Entry<String, Checker> entry : addonCheckers.entrySet()) {
                try { entry.getValue().check(source, reporter(findings, source)); }
                catch (RuntimeException | LinkageError failure) {
                    status = merge(status, Status.FAILED);
                    findings.add(new Finding(Severity.WARN, 0, 0, "cnpcplus.script.addon_check_failed", entry.getKey(),
                            source.sourceId(), source.sourceHash(), source.revision()));
                }
            }
        }
        if (!supported) status = merge(status, Status.UNSUPPORTED);
        return new CheckResult(status, findings, request.requestRevision(), request.source().main().sourceId(), "",
                request.registryRevision(), request.profileRevision());
    }

    /** Uses an immutable request snapshot and never applies its result to an editor automatically. */
    public static CompletableFuture<CheckResult> checkAsync(CheckRequest request) {
        if (request == null) throw new IllegalArgumentException("request");
        return CompletableFuture.supplyAsync(() -> {
            try { return check(request); }
            catch (RuntimeException | LinkageError failure) {
                return new CheckResult(Status.FAILED, Collections.<Finding>emptyList(), request.requestRevision(),
                        request.source().main().sourceId(), failure.getClass().getSimpleName(), request.registryRevision(), request.profileRevision());
            }
        });
    }

    private static Reporter reporter(List<Finding> findings, Source source) {
        return (severity, offset, length, key, subject) -> {
            if (severity == null || key == null || key.isEmpty()) return;
            int safeOffset = offset < 0 ? -1 : Math.min(offset, source.text().length());
            int safeLength = safeOffset < 0 ? 0 : Math.max(0, Math.min(length, source.text().length() - safeOffset));
            findings.add(new Finding(severity, safeOffset, safeLength, key,
                    subject == null ? "" : subject, source.sourceId(), source.sourceHash(), source.revision()));
        };
    }

    private static Finding fromDiagnostic(ScriptDiagnostic diagnostic, Source source) {
        Severity severity = diagnostic.severity == ScriptDiagnostic.Severity.ERROR ? Severity.ERROR
                : diagnostic.severity == ScriptDiagnostic.Severity.INFO ? Severity.INFO : Severity.WARN;
        int offset = diagnostic.offset < 0 ? -1 : Math.min(diagnostic.offset, source.text().length());
        int length = offset < 0 ? 0 : Math.max(0, Math.min(diagnostic.length, source.text().length() - offset));
        return new Finding(severity, offset, length, diagnostic.warningKey,
                diagnostic.token, source.sourceId(), source.sourceHash(), source.revision());
    }

    private static List<SourceUnit> units(SourceBundle bundle) {
        List<SourceUnit> values = new ArrayList<>(); values.add(bundle.main()); values.addAll(bundle.linked()); return values;
    }

    private static Status normalize(Status status) { return status == null ? Status.FAILED : status; }
    private static Status merge(Status left, Status right) {
        if (left == Status.FAILED || right == Status.FAILED) return Status.FAILED;
        if (left == Status.TIMED_OUT || right == Status.TIMED_OUT) return Status.TIMED_OUT;
        if (left == Status.DEPENDENCY_MISSING || right == Status.DEPENDENCY_MISSING) return Status.DEPENDENCY_MISSING;
        if (left == Status.UNSUPPORTED || right == Status.UNSUPPORTED) return Status.UNSUPPORTED;
        return Status.SUCCESS;
    }

    private static boolean identifier(String name) {
        if (name == null || name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        return true;
    }
}
