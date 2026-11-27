package bin.cnpcplus.api.script;

import java.net.URI;

/** One independently located source in a committed script snapshot. */
public final class SourceUnit {
    private final String sourceId, displayName, text;
    private final URI uri;

    public SourceUnit(String sourceId, String displayName, String text, URI uri) {
        if (sourceId == null || sourceId.isEmpty()) throw new IllegalArgumentException("sourceId");
        this.sourceId = sourceId;
        this.displayName = displayName == null ? sourceId : displayName;
        this.text = normalizeText(text);
        this.uri = uri;
    }

    public SourceUnit(String sourceId, String displayName, String text) { this(sourceId, displayName, text, null); }
    public String sourceId() { return sourceId; }
    public String displayName() { return displayName; }
    public String text() { return text; }
    public URI uri() { return uri; }

    public static String normalizeText(String text) {
        return (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n');
    }
}
