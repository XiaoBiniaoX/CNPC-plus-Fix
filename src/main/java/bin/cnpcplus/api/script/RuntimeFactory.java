package bin.cnpcplus.api.script;

/** Lazily creates one runtime after the first actual hook for a page. */
public interface RuntimeFactory {
    ScriptRuntime create(PageContext context) throws Exception;
}
