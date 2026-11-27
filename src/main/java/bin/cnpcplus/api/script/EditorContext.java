package bin.cnpcplus.api.script;

/** Immutable editor draft snapshot; its revision is independent of the server runtime revision. */
public final class EditorContext {
    private final ScriptPage page;
    private final SourceBundle draft;
    private final long draftRevision;
    private final String languageId, contextKind;
    private final ScriptMetadataApi.MetadataContext metadata;

    public EditorContext(ScriptPage page, SourceBundle draft, long draftRevision, String languageId,
            String contextKind, ScriptMetadataApi.MetadataContext metadata) {
        if (draft == null || metadata == null) throw new IllegalArgumentException("draft/metadata");
        this.page = page; this.draft = draft; this.draftRevision = draftRevision;
        this.languageId = languageId == null ? "" : languageId;
        this.contextKind = contextKind == null ? ScriptMetadataApi.UNKNOWN : contextKind;
        this.metadata = metadata;
    }

    public ScriptPage page() { return page; }
    public SourceBundle draft() { return draft; }
    public String draftText() { return draft.main().text(); }
    public long draftRevision() { return draftRevision; }
    public String languageId() { return languageId; }
    public String contextKind() { return contextKind; }
    public ScriptMetadataApi.MetadataContext metadata() { return metadata; }
}
