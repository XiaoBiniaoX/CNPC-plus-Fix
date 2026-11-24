package bin.cnpcplus.script.tools;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OperationResult<T> {
    public static final class Diagnostic {
        private final String code, severity, message, source, timestamp, suggestion;
        private final Map<String, String> context;
        public Diagnostic(String code, String severity, String message, String source, String timestamp, Map<String, String> context, String suggestion) {
            this.code = code; this.severity = severity; this.message = message; this.source = source;
            this.timestamp = timestamp; this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context)); this.suggestion = suggestion;
        }
        public String code() { return code; }
        public String severity() { return severity; }
        public String message() { return message; }
        public String source() { return source; }
        public String timestamp() { return timestamp; }
        public Map<String, String> context() { return context; }
        public String suggestion() { return suggestion; }
    }
    private final T value;
    private final String status;
    private final Diagnostic diagnostic;
    private OperationResult(T value, String status, Diagnostic diagnostic) { this.value = value; this.status = status; this.diagnostic = diagnostic; }
    public static <T> OperationResult<T> success(T value) { return new OperationResult<>(value, "SUCCESS", null); }
    public static <T> OperationResult<T> failure(String module, String reason) {
        return new OperationResult<>(null, reason, new Diagnostic("CNPCPLUS." + module + "." + reason, "WARNING",
                reason.replace('_', ' '), module, Instant.now().toString(), Collections.emptyMap(), "Check inputs and loaded world state"));
    }
    public boolean isSuccess() { return diagnostic == null; }
    public String getStatus() { return status; }
    public T getValue() { return value; }
    public String getReason() { return isSuccess() ? null : status; }
    public Diagnostic getDiagnostic() { return diagnostic; }
}
