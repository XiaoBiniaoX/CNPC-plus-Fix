package bin.cnpcplus.api.script;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable description of the one synchronous hook/callback currently executing. */
public final class InvocationContext {
    public enum Kind { HOOK, CALLBACK }
    private final PageContext pageContext;
    private final Kind kind;
    private final String hook;
    private final Object event;
    private final Map<String, Object> bindings;

    public InvocationContext(PageContext pageContext, Kind kind, String hook, Object event, Map<String, Object> bindings) {
        this.pageContext = pageContext; this.kind = kind; this.hook = hook; this.event = event;
        this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }

    public PageContext pageContext() { return pageContext; }
    public Kind kind() { return kind; }
    public Optional<String> hook() { return Optional.ofNullable(hook); }
    public Optional<Object> event() { return Optional.ofNullable(event); }
    public Map<String, Object> bindings() { return bindings; }
}
