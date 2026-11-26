package top.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import bin.cnpcplus.script.ApiCompletion;

/** Text-only editor extension. Safe on a dedicated server; never loads a GUI or evaluates source. */
public final class ScriptCompletionApi {
    /** Exact, case-insensitive engine labels. Built-in completion/checks understand CNPC ES5. */
    public record Language(Set<String> aliases, boolean builtinCompletion, boolean builtinChecks, String helpText) {
        public Language {
            Objects.requireNonNull(aliases, "aliases");
            aliases = aliases.stream().map(ScriptCompletionApi::normalize).collect(Collectors.toUnmodifiableSet());
            if (aliases.isEmpty() || aliases.contains("")) throw new IllegalArgumentException("aliases");
            helpText = Objects.requireNonNull(helpText, "helpText");
        }
    }

    /** All offsets are UTF-16; hooks are the current server-supplied method names. */
    public record Request(String text, int cursor, String language, String context, List<String> hooks, boolean explicit) {
        public Request {
            text = text == null ? "" : text;
            cursor = Math.max(0, Math.min(text.length(), cursor));
            language = language == null ? "" : language;
            context = context == null ? "NpcEvent" : context;
            hooks = hooks == null ? List.of() : List.copyOf(hooks);
        }
    }

    /** Replace [start,end) with LF-only insertText; caret is relative to that insertion. */
    public record Suggestion(String label, String insertText, String detail, int start, int end, int caret) {
        public Suggestion {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(insertText, "insertText");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** Final order/filtering; signature/error strings are literal text, not script NBT. */
    public record Result(List<Suggestion> suggestions, List<String> signatures, String error) {
        public Result {
            suggestions = List.copyOf(suggestions);
            signatures = List.copyOf(signatures);
            Objects.requireNonNull(error, "error");
        }
        public Result(List<Suggestion> suggestions, List<String> signatures) { this(suggestions, signatures, ""); }
    }

    /** Reflection metadata only; property aliases depend on the addon's engine interop. */
    public record Member(String name, String insertText, String signature, String typeName, boolean method) {}

    @FunctionalInterface
    public interface Provider { Result complete(Request request, Result defaults); }

    private record Registration(Language language, Provider provider) {}
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static final Language ES5 = new Language(Set.of("javascript", "js", "ecmascript", "nashorn",
            "javascript es5", "js es5", "ecmascript 5", "ecmascript 5.1"), true, true, "");
    private static volatile Map<String, Registration> registrations = Map.of();
    private static volatile long revision;
    private ScriptCompletionApi() {}

    /** Same ID replaces its registration. Different IDs may not own the same language alias. */
    public static synchronized void register(String id, Language language, Provider provider) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("id");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(provider, "provider");
        for (var entry : registrations.entrySet()) {
            if (!entry.getKey().equals(id) && entry.getValue().language.aliases.stream().anyMatch(language.aliases::contains))
                throw new IllegalArgumentException("Language alias already owned by " + entry.getKey());
        }
        var updated = new LinkedHashMap<>(registrations);
        updated.put(id, new Registration(language, provider));
        registrations = Map.copyOf(updated);
        revision++;
    }

    public static synchronized void unregister(String id) {
        if (id == null || !registrations.containsKey(id)) return;
        var updated = new LinkedHashMap<>(registrations);
        updated.remove(id);
        registrations = Map.copyOf(updated);
        revision++;
    }

    /** Immutable, process-local snapshot; no automatic client/server synchronization. */
    public static Set<String> registeredIds() { return registrations.keySet(); }
    public static long revision() { return revision; }

    public static Language language(String name) {
        String alias = normalize(name);
        Registration registered = find(alias);
        return registered != null ? registered.language : ES5.aliases.contains(alias) ? ES5 : null;
    }

    public static boolean supports(String language) { return language(language) != null; }
    public static boolean usesBuiltinChecks(String language) {
        Language profile = language(language);
        return profile != null && profile.builtinChecks;
    }

    /** Runs synchronously on the caller's thread. No client singleton, engine or world is accessed. */
    public static Result complete(Request request) {
        Objects.requireNonNull(request, "request");
        String engineName = ScriptLanguageApi.engineName(request.language);
        if (!engineName.equals(request.language)) request = new Request(request.text, request.cursor, engineName,
                request.context, request.hooks, request.explicit);
        String alias = normalize(request.language);
        Registration registered = find(alias);
        Language profile = registered != null ? registered.language : ES5.aliases.contains(alias) ? ES5 : null;
        Result defaults = new Result(List.of(), List.of());
        if (profile != null && profile.builtinCompletion) {
            var completion = ApiCompletion.complete(request.text, request.cursor, request.context, request.hooks);
            var suggestions = new ArrayList<Suggestion>();
            int end = request.cursor;
            while (end < request.text.length() && Character.isJavaIdentifierPart(request.text.charAt(end))) end++;
            if (request.explicit || completion.start() != request.cursor
                    || request.cursor > 0 && request.text.charAt(request.cursor - 1) == '.') {
                for (var entry : completion.entries()) {
                    String insert = end < request.text.length() && request.text.charAt(end) == '(' && entry.method()
                            ? entry.name() : entry.insert();
                    suggestions.add(new Suggestion(entry.signature(), insert, entry.signature(), completion.start(), end, insert.length()));
                }
            }
            defaults = new Result(suggestions, ApiCompletion.hint(request.text, request.cursor, request.context)
                    .stream().map(ApiCompletion.Entry::signature).toList());
        }
        if (registered == null) return defaults;
        try {
            Result result = Objects.requireNonNull(registered.provider.complete(request, defaults), "provider result");
            for (Suggestion suggestion : result.suggestions) {
                if (suggestion.start < 0 || suggestion.end < suggestion.start || suggestion.end > request.text.length()
                        || suggestion.caret < 0 || suggestion.caret > suggestion.insertText.length()
                        || suggestion.insertText.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("Invalid replacement range/caret or non-LF insertion");
            }
            return result;
        } catch (RuntimeException | LinkageError failure) {
            return new Result(defaults.suggestions, defaults.signatures,
                    "Completion provider: " + failure.getClass().getSimpleName());
        }
    }

    /** Public/inherited CNPC and Mixin-added members, without constructing objects or invoking methods. */
    public static List<Member> members(Class<?> owner) {
        return ApiCompletion.members(owner).stream().map(entry -> new Member(entry.name(), entry.insert(),
                entry.signature(), entry.type() == null ? "?" : entry.type().getTypeName(), entry.method())).toList();
    }

    private static Registration find(String alias) {
        for (Registration registered : registrations.values())
            if (registered.language.aliases.stream().map(ScriptCompletionApi::normalize).anyMatch(alias::equals)) return registered;
        return null;
    }
    private static String normalize(String language) { return ScriptLanguageApi.engineName(language).strip().toLowerCase(Locale.ROOT); }
}
