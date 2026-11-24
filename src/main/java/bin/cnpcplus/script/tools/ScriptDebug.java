package bin.cnpcplus.script.tools;

import java.time.Instant;
import java.util.*;
import org.apache.logging.log4j.*;

public final class ScriptDebug {
    private static final Logger LOG = LogManager.getLogger("cnpcplus.script");
    private static final Deque<OperationResult.Diagnostic> RECENT = new ArrayDeque<>();
    public void log(String level, String message) {
        String severity = level.toUpperCase(Locale.ROOT);
        if (!Arrays.asList("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(severity)) throw new IllegalArgumentException("Unknown log level");
        LOG.log(Level.valueOf(severity), "{}", new ScriptText().truncate(message, 2048));
        if (severity.equals("WARN") || severity.equals("ERROR")) record(new OperationResult.Diagnostic("CNPCPLUS.DEBUG.SCRIPT_LOG",
                severity.equals("WARN") ? "WARNING" : severity, "Script logged a message. See the server log.", "Debug", Instant.now().toString(), Collections.emptyMap(), ""));
    }
    public String inspect(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "String[" + ((String)value).length() + "]";
        if (value instanceof Number || value instanceof Boolean || value instanceof Position) return new ScriptText().truncate(value.toString(), 2048);
        if (value instanceof OperationResult<?>) return "OperationResult[" + ((OperationResult<?>)value).getStatus() + "]";
        return value.getClass().getSimpleName();
    }
    public static final class Metrics {
        private final int schedulerTasks; private final ScriptAsync.Metrics async;
        Metrics(int count, ScriptAsync.Metrics async) { schedulerTasks = count; this.async = async; }
        public int schedulerTasks() { return schedulerTasks; } public ScriptAsync.Metrics async() { return async; }
    }
    public static final class Capture {
        private final String timestamp; private final Metrics metrics; private final List<OperationResult.Diagnostic> diagnostics;
        Capture(String time, Metrics metrics, List<OperationResult.Diagnostic> diagnostics) { timestamp = time; this.metrics = metrics; this.diagnostics = diagnostics; }
        public String timestamp() { return timestamp; } public Metrics metrics() { return metrics; } public List<OperationResult.Diagnostic> diagnostics() { return diagnostics; }
    }
    public Metrics getMetrics() { return new Metrics(ScriptScheduler.taskCount(), new ScriptAsync().getMetrics()); }
    public Capture capture() { return new Capture(Instant.now().toString(), getMetrics(), getDiagnostics()); }
    public List<OperationResult.Diagnostic> getDiagnostics() { synchronized (RECENT) { return Collections.unmodifiableList(new ArrayList<>(RECENT)); } }
    static void record(OperationResult.Diagnostic diagnostic) { synchronized (RECENT) { if (RECENT.size() == 64) RECENT.removeFirst(); RECENT.addLast(diagnostic); } }
    public static void stop() { synchronized (RECENT) { RECENT.clear(); } }
}
