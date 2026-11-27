package bin.cnpcplus.api.script;

/** Generation-bound lifetime handle that may be captured by delayed callbacks. */
public interface ScriptScope {
    boolean isAlive();
    void manage(AutoCloseable resource);
}
