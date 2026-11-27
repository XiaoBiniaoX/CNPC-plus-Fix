package bin.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import bin.cnpcplus.script.ScriptRuntimeHost;

/** Structured, generation-checked runtime diagnostics. Reports are not serialized as Throwable objects. */
public final class ScriptDiagnosticApi {
    public enum Severity { ERROR, WARN, INFO }
    private static final Pattern CODE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final class Diagnostic {
        private static final int MAX_SOURCE_ID = 256, MAX_SOURCE_HASH = 64, MAX_CODE = 128;
        private static final int MAX_TRANSLATION_KEY = 128, MAX_ARGUMENTS = 4, MAX_ARGUMENT = 128;
        private final String sourceId, sourceHash, code, message, translationKey;
        private final long revision;
        private final Severity severity;
        private final int offset, length, line, column;
        private final List<String> arguments;
        private final Throwable cause;

        public Diagnostic(String sourceId, long revision, String sourceHash, Severity severity, String code,
                String message, String translationKey, List<String> arguments, int offset, int length,
                int line, int column, Throwable cause) {
            if (sourceId == null || sourceId.isEmpty() || sourceId.length() > MAX_SOURCE_ID) throw new IllegalArgumentException("sourceId");
            if (code == null || code.length() > MAX_CODE || !CODE.matcher(code).matches() || code.contains("..")) throw new IllegalArgumentException("code");
            if (sourceHash != null && sourceHash.length() > MAX_SOURCE_HASH) throw new IllegalArgumentException("sourceHash");
            if (offset < -1) throw new IllegalArgumentException("offset");
            if (offset >= 0 && (length < 0 || line < 1 || column < 1)) throw new IllegalArgumentException("located range");
            this.sourceId = sourceId; this.revision = revision; this.sourceHash = sourceHash == null ? "" : sourceHash;
            this.severity = Objects.requireNonNull(severity, "severity");
            this.code = code; this.message = bounded(message, 1024);
            this.translationKey = bounded(translationKey, MAX_TRANSLATION_KEY);
            List<String> copied = new ArrayList<>();
            if (arguments != null) for (String argument : arguments) {
                if (copied.size() == MAX_ARGUMENTS) break;
                copied.add(bounded(argument, MAX_ARGUMENT));
            }
            this.arguments = Collections.unmodifiableList(copied);
            this.offset = Math.max(-1, offset); this.length = offset < 0 ? 0 : length;
            this.line = offset < 0 ? 0 : Math.max(0, line); this.column = offset < 0 ? 0 : Math.max(0, column);
            this.cause = cause;
        }

        public String sourceId() { return sourceId; }
        public long revision() { return revision; }
        public String sourceHash() { return sourceHash; }
        public Severity severity() { return severity; }
        public String code() { return code; }
        public String message() { return message; }
        public String translationKey() { return translationKey; }
        public List<String> arguments() { return arguments; }
        public int offset() { return offset; }
        public int length() { return length; }
        public int line() { return line; }
        public int column() { return column; }
        public Throwable cause() { return cause; }
    }

    private ScriptDiagnosticApi() {}

    /** Returns false when the scope, revision, source ID or source hash is stale. Server thread only. */
    public static boolean report(ScriptScope scope, Diagnostic diagnostic) {
        if (scope == null || diagnostic == null) throw new IllegalArgumentException("scope/diagnostic");
        return ScriptRuntimeHost.reportDiagnostic(scope, diagnostic);
    }

    public static List<Diagnostic> diagnostics(ScriptPage page) {
        if (page == null) throw new IllegalArgumentException("page");
        return ScriptRuntimeHost.diagnostics(page);
    }

    /** SHA-256 of LF-normalized source text, matching SourceUnit and report validation. */
    public static String sourceHash(String text) {
        return bin.cnpcplus.script.ScriptDiagnostic.hash(SourceUnit.normalizeText(text));
    }

    private static String bounded(String value, int limit) {
        String text = value == null ? "" : value;
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
