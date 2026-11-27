package bin.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable, side-neutral description of a script language. */
public final class LanguageDescriptor {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private final String id, displayName, displayNameKey, languageVersion, descriptionKey, limitationsKey;
    private final Set<String> aliases;
    private final List<String> extensions;

    public LanguageDescriptor(String id, String displayName, String displayNameKey, Set<String> aliases,
            List<String> extensions, String languageVersion, String descriptionKey, String limitationsKey) {
        if (id == null || !ID.matcher(id).matches() || id.contains("..")) throw new IllegalArgumentException("Invalid language id: " + id);
        if (displayName == null || displayName.trim().isEmpty()) throw new IllegalArgumentException("displayName");
        this.id = id;
        this.displayName = displayName;
        this.displayNameKey = empty(displayNameKey);
        Set<String> aliasCopy = new LinkedHashSet<>();
        if (aliases != null) for (String alias : aliases) {
            String value = normalize(alias);
            if (value.isEmpty()) throw new IllegalArgumentException("Empty language alias");
            aliasCopy.add(value);
        }
        this.aliases = Collections.unmodifiableSet(aliasCopy);
        List<String> extensionCopy = new ArrayList<>();
        if (extensions != null) for (String extension : extensions) {
            if (extension == null || !extension.matches("\\.[a-zA-Z0-9._+-]+"))
                throw new IllegalArgumentException("Invalid language extension: " + extension);
            String value = extension.toLowerCase(Locale.ROOT);
            if (!extensionCopy.contains(value)) extensionCopy.add(value);
        }
        this.extensions = Collections.unmodifiableList(extensionCopy);
        this.languageVersion = empty(languageVersion);
        this.descriptionKey = empty(descriptionKey);
        this.limitationsKey = empty(limitationsKey);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String displayNameKey() { return displayNameKey; }
    public Set<String> aliases() { return aliases; }
    public List<String> extensions() { return extensions; }
    public String languageVersion() { return languageVersion; }
    public String descriptionKey() { return descriptionKey; }
    public String limitationsKey() { return limitationsKey; }

    static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String empty(String value) { return value == null ? "" : value; }
}
