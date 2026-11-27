package bin.cnpcplus.api.script;

import java.io.Writer;

/** Stable context for a single page generation. Writers ignore output after that generation ends. */
public final class PageContext {
    private final ScriptPage page;
    private final ScriptScope scope;
    private final Writer stdout, stderr;
    private final ClassLoader runtimeClassLoader;

    public PageContext(ScriptPage page, ScriptScope scope, Writer stdout, Writer stderr, ClassLoader runtimeClassLoader) {
        this.page = page; this.scope = scope; this.stdout = stdout; this.stderr = stderr;
        this.runtimeClassLoader = runtimeClassLoader;
    }

    public ScriptPage page() { return page; }
    public ScriptScope scope() { return scope; }
    public Writer stdout() { return stdout; }
    public Writer stderr() { return stderr; }
    public ClassLoader runtimeClassLoader() { return runtimeClassLoader; }
}
