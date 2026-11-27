package bin.cnpcplus.api.script;

import bin.cnpcplus.script.ScriptApiCompletion;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Server-safe, text-only completion profiles. Providers return suggestions; they never edit or execute source. */
public final class ScriptCompletionApi {
    public static final class Language {
        private final Set<String> aliases;
        private final boolean builtinCompletion, builtinChecks;
        private final String helpText, helpTranslationKey;
        private final SyntaxProvider syntaxProvider;
        private final TemplateProvider templateProvider;

        public Language(Set<String> aliases, boolean builtinCompletion, boolean builtinChecks, String helpText) {
            this(aliases, builtinCompletion, builtinChecks, helpText, "", null, null);
        }

        public Language(Set<String> aliases, boolean builtinCompletion, boolean builtinChecks, String helpText,
                String helpTranslationKey, SyntaxProvider syntaxProvider, TemplateProvider templateProvider) {
            if (aliases == null || aliases.isEmpty()) throw new IllegalArgumentException("aliases");
            Set<String> normalized = new LinkedHashSet<>();
            for (String alias : aliases) {
                String value = normalize(alias);
                if (value.isEmpty()) throw new IllegalArgumentException("aliases");
                normalized.add(value);
            }
            this.aliases = Collections.unmodifiableSet(normalized);
            this.builtinCompletion = builtinCompletion;
            this.builtinChecks = builtinChecks;
            this.helpText = helpText == null ? "" : helpText;
            this.helpTranslationKey = helpTranslationKey == null ? "" : helpTranslationKey;
            this.syntaxProvider = syntaxProvider;
            this.templateProvider = templateProvider;
        }

        public Set<String> aliases() { return aliases; }
        public boolean builtinCompletion() { return builtinCompletion; }
        public boolean builtinChecks() { return builtinChecks; }
        public String helpText() { return helpText; }
        public String helpTranslationKey() { return helpTranslationKey; }
    }

    /** Offsets use Java UTF-16 indices, matching the editor text model. */
    public static final class Request {
        private final String text, language, context;
        private final int cursor;
        private final List<String> hooks;
        private final boolean explicit;

        public Request(String text, int cursor, String language, String context, List<String> hooks, boolean explicit) {
            this.text = text == null ? "" : text;
            this.cursor = Math.max(0, Math.min(this.text.length(), cursor));
            this.language = language == null ? "" : language;
            this.context = context == null || context.isEmpty() ? "NpcEvent" : context;
            List<String> hookCopy = new ArrayList<>(hooks == null ? Collections.<String>emptyList() : hooks);
            for (String hook : hookCopy) Objects.requireNonNull(hook, "hook");
            this.hooks = Collections.unmodifiableList(hookCopy);
            this.explicit = explicit;
        }

        public String text() { return text; }
        public int cursor() { return cursor; }
        public String language() { return language; }
        public String context() { return context; }
        public List<String> hooks() { return hooks; }
        public boolean explicit() { return explicit; }
    }

    /** Replace [start,end) with insertText, then place the caret at start + caret. */
    public static final class Suggestion {
        private final String label, insertText, detail;
        private final int start, end, caret;

        public Suggestion(String label, String insertText, String detail, int start, int end, int caret) {
            this.label = Objects.requireNonNull(label, "label");
            this.insertText = Objects.requireNonNull(insertText, "insertText");
            this.detail = detail == null ? "" : detail;
            this.start = start;
            this.end = end;
            this.caret = caret;
        }

        public String label() { return label; }
        public String insertText() { return insertText; }
        public String detail() { return detail; }
        public int start() { return start; }
        public int end() { return end; }
        public int caret() { return caret; }
    }

    public static final class Result {
        private final List<Suggestion> suggestions;
        private final List<String> signatures;
        private final String error;

        public Result(List<Suggestion> suggestions, List<String> signatures) { this(suggestions, signatures, ""); }

        public Result(List<Suggestion> suggestions, List<String> signatures, String error) {
            List<Suggestion> suggestionCopy = new ArrayList<>(suggestions == null
                    ? Collections.<Suggestion>emptyList() : suggestions);
            for (Suggestion suggestion : suggestionCopy) Objects.requireNonNull(suggestion, "suggestion");
            List<String> signatureCopy = new ArrayList<>(signatures == null
                    ? Collections.<String>emptyList() : signatures);
            for (String signature : signatureCopy) Objects.requireNonNull(signature, "signature");
            this.suggestions = Collections.unmodifiableList(suggestionCopy);
            this.signatures = Collections.unmodifiableList(signatureCopy);
            this.error = error == null ? "" : error;
        }

        public List<Suggestion> suggestions() { return suggestions; }
        public List<String> signatures() { return signatures; }
        public String error() { return error; }
    }

    public enum SyntaxCategory { DEFAULT, KEYWORD, STRING, NUMBER, COMMENT, FUNCTION, TYPE, OPERATOR }

    /** Non-overlapping UTF-16 source range. */
    public static final class TextSpan {
        private final int start, end;
        private final SyntaxCategory category;
        public TextSpan(int start, int end, SyntaxCategory category) {
            this.start = start; this.end = end; this.category = Objects.requireNonNull(category, "category");
        }
        public int start() { return start; }
        public int end() { return end; }
        public SyntaxCategory category() { return category; }
    }

    public static final class ColoringResult {
        private final List<TextSpan> spans;
        private final String error;
        ColoringResult(List<TextSpan> spans, String error) {
            this.spans = Collections.unmodifiableList(new ArrayList<>(spans)); this.error = error == null ? "" : error;
        }
        public List<TextSpan> spans() { return spans; }
        public String error() { return error; }
    }

    public static final class TemplateResult {
        private final Suggestion suggestion;
        private final String error;
        TemplateResult(Suggestion suggestion, String error) { this.suggestion = suggestion; this.error = error == null ? "" : error; }
        public Suggestion suggestion() { return suggestion; }
        public String error() { return error; }
    }

    public interface SyntaxProvider { List<TextSpan> color(EditorContext context); }

    public interface TemplateProvider { Suggestion create(EditorContext context, ScriptMetadataApi.HookInfo hook, String indentation); }

    /** Reflection metadata only; this does not instantiate the class or invoke members. */
    public static final class Member {
        private final String name, insertText, signature, typeName;
        private final boolean method;

        private Member(String name, String insertText, String signature, String typeName, boolean method) {
            this.name = name;
            this.insertText = insertText;
            this.signature = signature;
            this.typeName = typeName;
            this.method = method;
        }

        public String name() { return name; }
        public String insertText() { return insertText; }
        public String signature() { return signature; }
        public String typeName() { return typeName; }
        public boolean method() { return method; }
    }

    public interface Provider {
        /** Return final suggestions for this request. The input and defaults are immutable snapshots. */
        Result complete(Request request, Result defaults);
    }

    private static final class Registration {
        final Language language;
        final Provider provider;
        Registration(Language language, Provider provider) { this.language = language; this.provider = provider; }
    }

    public static final class CheckProfile {
        private final long revision;
        private final boolean builtinChecks;
        private CheckProfile(long revision, boolean builtinChecks) { this.revision = revision; this.builtinChecks = builtinChecks; }
        public long revision() { return revision; }
        public boolean builtinChecks() { return builtinChecks; }
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static final Language ES5 = new Language(new LinkedHashSet<>(java.util.Arrays.asList(
            "javascript", "js", "ecmascript", "nashorn", "javascript es5", "js es5", "ecmascript 5", "ecmascript 5.1")), true, true, "");
    private static volatile Map<String, Registration> registrations = Collections.emptyMap();
    private static volatile long revision;

    private ScriptCompletionApi() {}

    /** Register during addon setup on each process that owns an editor. Re-registering the same ID replaces it. */
    public static synchronized void register(String id, Language language, Provider provider) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(provider, "provider");
        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            if (!entry.getKey().equals(id)) for (String alias : language.aliases) {
                if (entry.getValue().language.aliases.contains(alias))
                    throw new IllegalArgumentException("Language alias already owned by " + entry.getKey());
            }
        }
        Map<String, Registration> updated = new LinkedHashMap<>(registrations);
        updated.put(id, new Registration(language, provider));
        registrations = Collections.unmodifiableMap(updated);
        revision++;
    }

    public static synchronized void unregister(String id) {
        if (id == null || !registrations.containsKey(id)) return;
        Map<String, Registration> updated = new LinkedHashMap<>(registrations);
        updated.remove(id);
        registrations = Collections.unmodifiableMap(updated);
        revision++;
    }

    public static Set<String> registeredIds() { return registrations.keySet(); }
    public static long revision() { return revision; }

    /** Registered aliases override the built-in JavaScript profile. */
    public static Language language(String name) {
        String alias = normalize(name);
        Registration registered = find(alias);
        return registered == null ? builtinProfileFor(alias) : registered.language;
    }

    public static boolean supports(String name) { return language(name) != null; }
    /** Atomically captures the profile flag and registry version used by asynchronous checks. */
    public static synchronized CheckProfile checkProfile(String name) {
        Language profile = language(name);
        return new CheckProfile(revision, profile != null && profile.builtinChecks);
    }

    public static boolean usesBuiltinChecks(String name) {
        Language profile = language(name);
        return profile != null && profile.builtinChecks;
    }

    public static ColoringResult color(EditorContext context) {
        Objects.requireNonNull(context, "context");
        Registration registered = find(context.languageId());
        if (registered == null || registered.language.syntaxProvider == null)
            return new ColoringResult(Collections.<TextSpan>emptyList(), "");
        try {
            List<TextSpan> spans = new ArrayList<>(Objects.requireNonNull(registered.language.syntaxProvider.color(context), "syntax spans"));
            int end = 0;
            for (TextSpan span : spans) {
                if (span == null || span.start < end || span.start < 0 || span.end <= span.start
                        || span.end > context.draftText().length()) throw new IllegalArgumentException("Invalid syntax span");
                end = span.end;
            }
            return new ColoringResult(spans, "");
        } catch (RuntimeException | LinkageError failure) {
            return new ColoringResult(Collections.<TextSpan>emptyList(), failure.getClass().getSimpleName());
        }
    }

    public static Suggestion template(EditorContext context, ScriptMetadataApi.HookInfo hook, String indentation) {
        return templateResult(context, hook, indentation).suggestion();
    }

    public static TemplateResult templateResult(EditorContext context, ScriptMetadataApi.HookInfo hook, String indentation) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(hook, "hook");
        Registration registered = find(context.languageId());
        if (registered == null || registered.language.templateProvider == null) return new TemplateResult(null, "");
        try {
            Suggestion suggestion = registered.language.templateProvider.create(context, hook, indentation == null ? "" : indentation);
            if (suggestion == null) return new TemplateResult(null, "");
            if (suggestion.start < 0 || suggestion.end < suggestion.start || suggestion.end > context.draftText().length()
                    || suggestion.caret < 0 || suggestion.caret > suggestion.insertText.length()
                    || suggestion.insertText.indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid event template range");
            return new TemplateResult(suggestion, "");
        } catch (RuntimeException | LinkageError failure) { return new TemplateResult(null, failure.getClass().getSimpleName()); }
    }

    /** Synchronous and side-neutral. Never accesses the client, world, script engine, or editor. */
    public static Result complete(Request request) {
        Objects.requireNonNull(request, "request");
        Registration registered = find(request.language);
        Language profile = registered == null ? builtinProfileFor(normalize(request.language)) : registered.language;
        Result defaults = new Result(Collections.<Suggestion>emptyList(), Collections.<String>emptyList());
        if (profile != null && profile.builtinCompletion) {
            ScriptApiCompletion.Result completion = ScriptApiCompletion.complete(
                    request.text, request.cursor, request.context, request.hooks);
            int end = request.cursor;
            while (end < request.text.length() && Character.isJavaIdentifierPart(request.text.charAt(end))) end++;
            List<Suggestion> suggestions = new ArrayList<>();
            if (request.explicit || completion.start != request.cursor
                    || request.cursor > 0 && request.text.charAt(request.cursor - 1) == '.') {
                for (ScriptApiCompletion.Entry entry : completion.entries) {
                    String insertion = end < request.text.length() && request.text.charAt(end) == '(' && entry.method
                            ? entry.name : entry.insert;
                    suggestions.add(new Suggestion(entry.signature, insertion, entry.signature,
                            completion.start, end, insertion.length()));
                }
            }
            List<String> signatures = new ArrayList<>();
            for (ScriptApiCompletion.Entry hint : ScriptApiCompletion.hint(request.text, request.cursor, request.context))
                signatures.add(hint.signature);
            defaults = new Result(suggestions, signatures);
        }
        if (registered == null) return defaults;
        try {
            Result result = Objects.requireNonNull(registered.provider.complete(request, defaults), "provider result");
            for (Suggestion suggestion : result.suggestions) {
                if (suggestion == null || suggestion.start < 0 || suggestion.end < suggestion.start
                        || suggestion.end > request.text.length() || suggestion.caret < 0
                        || suggestion.caret > suggestion.insertText.length() || suggestion.insertText.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("Invalid suggestion range, caret or line ending");
            }
            return result;
        } catch (RuntimeException | LinkageError failure) {
            return new Result(defaults.suggestions, defaults.signatures,
                    failure.getClass().getSimpleName());
        }
    }

    /** Public/inherited CNPC and Mixin-added members; metadata queries do not construct or invoke API objects. */
    public static List<Member> members(Class<?> owner) {
        List<Member> result = new ArrayList<>();
        for (ScriptApiCompletion.Entry entry : ScriptApiCompletion.members(owner)) {
            Type type = entry.type;
            result.add(new Member(entry.name, entry.insert, entry.signature,
                    type == null ? "?" : type.getTypeName(), entry.method));
        }
        return Collections.unmodifiableList(result);
    }

    private static Registration find(String alias) {
        String normalized = normalize(alias);
        Registration byId = registrations.get(normalized);
        if (byId != null) return byId;
        for (Registration registration : registrations.values())
            if (registration.language.aliases.contains(normalized)) return registration;
        java.util.Optional<LanguageDescriptor> descriptor = ScriptHostApi.language(alias);
        if (descriptor.isPresent()) {
            LanguageDescriptor language = descriptor.get();
            byId = registrations.get(language.id());
            if (byId != null) return byId;
            for (Registration registration : registrations.values()) {
                if (registration.language.aliases.contains(normalize(language.id()))) return registration;
                for (String languageAlias : language.aliases())
                    if (registration.language.aliases.contains(languageAlias)) return registration;
            }
        }
        return null;
    }

    private static Language builtinProfileFor(String alias) {
        java.util.Optional<LanguageDescriptor> descriptor = ScriptHostApi.language(alias);
        if (descriptor.isPresent()) for (String languageAlias : descriptor.get().aliases())
            if (ES5.aliases.contains(languageAlias)) return ES5;
        return alias.isEmpty() || ES5.aliases.contains(alias) ? ES5 : null;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
