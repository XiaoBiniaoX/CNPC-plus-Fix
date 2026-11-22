package top.cnpcplus.script.tools;

import java.time.Instant;
import java.util.Map;

public final class OperationResult<T> {
    public record Diagnostic(String code, String severity, String message, String source,
                             String timestamp, Map<String, String> context, String suggestion) {}

    private final T value;
    private final String status;
    private final Diagnostic diagnostic;

    private OperationResult(T value, String status, Diagnostic diagnostic) {
        this.value = value;
        this.status = status;
        this.diagnostic = diagnostic;
    }

    public static <T> OperationResult<T> success(T value) { return new OperationResult<>(value, "SUCCESS", null); }

    public static <T> OperationResult<T> failure(String module, String reason) {
        return new OperationResult<>(null, reason, new Diagnostic("CNPCPLUS." + module + "." + reason, "WARNING",
                reason.replace('_', ' '), module, Instant.now().toString(), Map.of(), "Check inputs and loaded world state"));
    }

    public boolean isSuccess() { return diagnostic == null; }
    public String getStatus() { return status; }
    public T getValue() { return value; }
    public String getReason() { return isSuccess() ? null : status; }
    public Diagnostic getDiagnostic() { return diagnostic; }
}
