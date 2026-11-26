package top.cnpcplus.api.script;

import java.util.Set;

/** Stable feature compatibility information for Java addons. */
public final class CnpcPlusApi {
    private static final Set<String> CAPABILITIES = Set.of(
            "script.completion.v1", "script.self-check.v1", "script.editor-providers.v1",
            "script.page-context.v1", "script.editor-client-actions.v1", "script.lifecycle.v1",
            "script.metadata.v1");

    private CnpcPlusApi() {}

    /** Public integration contract level; independent of the displayed mod version. */
    public static int apiLevel() { return 1; }

    /** Build-specific manifest value, with a source/dev fallback outside a packaged JAR. */
    public static String buildId() {
        String packaged = CnpcPlusApi.class.getPackage().getImplementationVersion();
        return packaged == null || packaged.isBlank() ? "cnpcplus-4.2.0-1.20.1Forge-dev-api1" : packaged;
    }

    public static Set<String> capabilities() { return CAPABILITIES; }
}
