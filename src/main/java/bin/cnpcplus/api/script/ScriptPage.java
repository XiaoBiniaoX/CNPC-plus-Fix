package bin.cnpcplus.api.script;

/** Public handle for one native CNPC script page. */
public interface ScriptPage {
    String contextKind();
    String languageOverride();
    String effectiveLanguageId();
    SourceBundle source();
    long generation();
    boolean isPresent();
    void setLanguageOverride(String languageIdOrEmpty);
    void reload();
}
