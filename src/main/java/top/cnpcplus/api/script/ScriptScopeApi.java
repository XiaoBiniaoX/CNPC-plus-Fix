package top.cnpcplus.api.script;

import java.util.Optional;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import top.cnpcplus.script.tools.ScriptScope;

/** Read-only view of the current thread's execution scope. No external scope replacement is supported. */
public final class ScriptScopeApi {
    /** Check on the owning game main thread before touching game objects. Once invalid, stays invalid. */
    @FunctionalInterface public interface ScriptLifetime { boolean isValid(); }
    /** Synchronous context only; do not retain or send to a background worker. */
    public record ScriptExecutionContext(ScriptContainer page, IScriptHandler handler, String hookName,
                                         String effectiveLanguage, String contextId) {}
    private ScriptScopeApi() {}
    public static Optional<ScriptExecutionContext> currentContext() { return ScriptScope.context(); }
    /** Also works inside scheduler/async/event callbacks that inherited an existing lifetime. */
    public static Optional<ScriptLifetime> captureLifetime() { return ScriptScope.capture(); }
}
