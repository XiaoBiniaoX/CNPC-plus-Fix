package top.cnpcplus.script.tools;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = "cnpcplus")
public final class ScriptDebug {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("cnpcplus.script");
    private static final ArrayDeque<OperationResult.Diagnostic> RECENT = new ArrayDeque<>();

    public void log(String level, String message) {
        String severity = level.toUpperCase(Locale.ROOT);
        String text = message.substring(0, Math.min(message.length(), 2048));
        switch (severity) {
            case "TRACE" -> LOG.trace("{}", text);
            case "DEBUG" -> LOG.debug("{}", text);
            case "INFO" -> LOG.info("{}", text);
            case "WARN" -> LOG.warn("{}", text);
            case "ERROR" -> LOG.error("{}", text);
            default -> throw new IllegalArgumentException("Unknown log level");
        }
        if (severity.equals("WARN") || severity.equals("ERROR")) {
            record(new OperationResult.Diagnostic("CNPCPLUS.DEBUG.SCRIPT_LOG", severity.equals("WARN") ? "WARNING" : severity,
                    "Script logged a message. See the server log.", "Debug", Instant.now().toString(), Map.of(), ""));
        }
    }

    public String inspect(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "String[" + text.length() + "]";
        if (value instanceof Number || value instanceof Boolean || value instanceof Position) return value.toString();
        if (value instanceof OperationResult<?> result) return "OperationResult[" + result.getStatus() + "]";
        if (value instanceof ScriptRayTrace.Result ray) return "RayTrace[" + ray.hitType() + ", distance=" + ray.hitDistance() + "]";
        if (value instanceof ScriptNavigation.PathSnapshot path) return "Navigation[" + path.status() + ", nodes=" + path.nodeCount() + "]";
        return value.getClass().getSimpleName();
    }

    public record Metrics(int schedulerTasks, ScriptAsync.Metrics async) {}
    public record Capture(String timestamp, Metrics metrics, List<OperationResult.Diagnostic> diagnostics) {}

    public Metrics getMetrics() { return new Metrics(ScriptScheduler.taskCount(), new ScriptAsync().getMetrics()); }
    public Capture capture() { return new Capture(Instant.now().toString(), getMetrics(), getDiagnostics()); }
    public List<OperationResult.Diagnostic> getDiagnostics() { synchronized (RECENT) { return List.copyOf(RECENT); } }

    static void record(OperationResult.Diagnostic diagnostic) {
        synchronized (RECENT) {
            if (RECENT.size() == 64) RECENT.removeFirst();
            RECENT.addLast(diagnostic);
        }
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { synchronized (RECENT) { RECENT.clear(); } }
}
