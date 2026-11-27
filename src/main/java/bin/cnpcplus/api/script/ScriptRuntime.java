package bin.cnpcplus.api.script;

import java.util.Map;

/** Per-page, per-generation runtime. Calls are made synchronously on the server thread. */
public interface ScriptRuntime extends AutoCloseable {
    void load(SourceBundle source, Map<String, Object> globals) throws Exception;
    boolean invoke(InvocationContext invocation) throws Exception;
    @Override void close();
}
