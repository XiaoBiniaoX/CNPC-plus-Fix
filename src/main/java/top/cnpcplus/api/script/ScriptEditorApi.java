package top.cnpcplus.api.script;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Text-only editor providers. This class is safe to load on a dedicated server. */
public final class ScriptEditorApi {
    public enum TokenKind { KEYWORD, STRING, NUMBER, COMMENT, TYPE, FUNCTION, DEFAULT }

    public record TextRequest(String text, String language, ScriptPageApi.Carrier carrier) {
        public TextRequest {
            text = text == null ? "" : text;
            language = language == null ? "" : language;
            carrier = carrier == null ? ScriptPageApi.Carrier.UNKNOWN : carrier;
        }
    }

    /** [start,end) is a UTF-16 range in the original request text. */
    public record TokenSpan(int start, int end, TokenKind kind) {}

    @FunctionalInterface
    public interface Highlighter {
        List<TokenSpan> highlight(TextRequest request);
    }

    public record TemplateRequest(String language, ScriptPageApi.Carrier carrier,
                                  ScriptPageApi.HookDescriptor hook, String documentText,
                                  int cursor, String lineIndent) {
        public TemplateRequest {
            language = language == null ? "" : language;
            carrier = carrier == null ? ScriptPageApi.Carrier.UNKNOWN : carrier;
            Objects.requireNonNull(hook, "hook");
            documentText = documentText == null ? "" : documentText;
            cursor = Math.max(0, Math.min(documentText.length(), cursor));
            lineIndent = lineIndent == null ? "" : lineIndent;
        }
    }

    /** caret is a UTF-16 offset within the LF-only inserted text. */
    public record Template(String insertText, int caret) {
        public Template {
            Objects.requireNonNull(insertText, "insertText");
        }
    }

    @FunctionalInterface
    public interface TemplateProvider {
        Optional<Template> create(TemplateRequest request);
    }

    private record Registration(Set<String> languages, Highlighter highlighter, TemplateProvider templates) {}
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static volatile Map<String, Registration> highlighters = Map.of();
    private static volatile Map<String, Registration> templates = Map.of();
    private static final Map<String, String> FAILURES = new ConcurrentHashMap<>();
    private static volatile long revision;

    private ScriptEditorApi() {}

    public static synchronized void registerHighlighter(String id, Set<String> languages, Highlighter provider) {
        validate(id, languages, provider);
        highlighters = replace(highlighters, id, normalize(languages), new Registration(normalize(languages), provider, null));
        revision++;
    }

    public static synchronized void registerTemplates(String id, Set<String> languages, TemplateProvider provider) {
        validate(id, languages, provider);
        templates = replace(templates, id, normalize(languages), new Registration(normalize(languages), null, provider));
        revision++;
    }

    public static synchronized void unregisterHighlighter(String id) {
        highlighters = remove(highlighters, id);
    }

    public static synchronized void unregisterTemplates(String id) {
        templates = remove(templates, id);
    }

    /** Empty means no provider or a safely isolated provider failure. */
    public static List<TokenSpan> highlight(TextRequest request) {
        Objects.requireNonNull(request, "request");
        var entry = find(highlighters, normalize(request.language));
        if (entry == null) return List.of();
        try {
            List<TokenSpan> spans = Objects.requireNonNull(entry.getValue().highlighter.highlight(request), "highlight result");
            int end = 0;
            for (TokenSpan span : spans) {
                if (span == null || span.kind == null || span.start < end || span.end <= span.start
                        || span.end > request.text.length()) throw new IllegalArgumentException("invalid token span");
                end = span.end;
            }
            FAILURES.remove(entry.getKey());
            return List.copyOf(spans);
        } catch (RuntimeException | LinkageError failure) {
            recordFailure(entry.getKey(), failure);
            return List.of();
        }
    }

    public static boolean hasHighlighter(String language) {
        return find(highlighters, normalize(language)) != null;
    }

    public static boolean hasTemplates(String language) {
        return find(templates, normalize(language)) != null;
    }

    public static Optional<Template> template(TemplateRequest request) {
        Objects.requireNonNull(request, "request");
        var entry = find(templates, normalize(request.language));
        if (entry == null) return Optional.empty();
        try {
            Optional<Template> result = Objects.requireNonNull(entry.getValue().templates.create(request), "template result");
            if (result.isPresent()) {
                Template template = result.get();
                if (template.caret < 0 || template.caret > template.insertText.length()
                        || template.insertText.indexOf('\r') >= 0) throw new IllegalArgumentException("invalid template caret or line ending");
            }
            FAILURES.remove(entry.getKey());
            return result;
        } catch (RuntimeException | LinkageError failure) {
            recordFailure(entry.getKey(), failure);
            return Optional.empty();
        }
    }

    /** Bounded provider status suitable for editor diagnostics; never exposes source text or stack traces. */
    public static String lastFailure(String id) { return FAILURES.getOrDefault(id, ""); }
    public static long revision() { return revision; }

    private static void validate(String id, Set<String> languages, Object provider) {
        if (id == null || !ID.matcher(id).matches() || provider == null || languages == null || languages.isEmpty())
            throw new IllegalArgumentException("id/languages/provider");
        if (normalize(languages).isEmpty()) throw new IllegalArgumentException("languages");
    }

    private static Map<String, Registration> replace(Map<String, Registration> current, String id,
                                                     Set<String> aliases, Registration registration) {
        for (var entry : current.entrySet()) {
            if (!entry.getKey().equals(id) && entry.getValue().languages.stream().anyMatch(aliases::contains))
                throw new IllegalArgumentException("Language alias already owned by " + entry.getKey());
        }
        var updated = new LinkedHashMap<>(current);
        updated.put(id, registration);
        return Map.copyOf(updated);
    }

    private static Map<String, Registration> remove(Map<String, Registration> current, String id) {
        if (id == null || !current.containsKey(id)) return current;
        var updated = new LinkedHashMap<>(current);
        updated.remove(id);
        FAILURES.remove(id);
        revision++;
        return Map.copyOf(updated);
    }

    private static Set<String> normalize(Set<String> aliases) {
        return aliases.stream().map(ScriptEditorApi::normalize).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalize(String language) { return language == null ? "" : language.strip().toLowerCase(Locale.ROOT); }

    private static Map.Entry<String, Registration> find(Map<String, Registration> source, String language) {
        for (var entry : source.entrySet()) if (entry.getValue().languages.contains(language)) return entry;
        return null;
    }

    private static void recordFailure(String id, Throwable failure) {
        if (FAILURES.size() >= 128 && !FAILURES.containsKey(id)) FAILURES.keySet().stream().findFirst().ifPresent(FAILURES::remove);
        FAILURES.put(id, failure.getClass().getSimpleName());
    }
}
