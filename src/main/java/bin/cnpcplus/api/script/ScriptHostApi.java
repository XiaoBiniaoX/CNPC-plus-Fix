package bin.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import bin.cnpcplus.script.ScriptRuntimeHost;
import noppes.npcs.controllers.ScriptController;

/** Stable, side-neutral public API for registering script languages and querying host contexts. */
public final class ScriptHostApi {
    public static final class Registration {
        private final LanguageDescriptor language;
        private final RuntimeFactory factory;
        private Registration(LanguageDescriptor language, RuntimeFactory factory) { this.language = language; this.factory = factory; }
        public LanguageDescriptor language() { return language; }
    }

    private static volatile Map<String, Registration> registrations = Collections.emptyMap();
    private static volatile Map<String, String> aliases = Collections.emptyMap();
    private ScriptHostApi() {}

    public static int apiMajor() { return 1; }
    public static int apiMinor() { return 1; }
    public static boolean hasCapability(String capability) {
        return "language-runtime".equals(capability) || "page-language".equals(capability)
                || "execution-scope".equals(capability) || "metadata".equals(capability)
                || "editor-language".equals(capability) || "structured-diagnostics".equals(capability)
                || "check-status".equals(capability) || "async-check".equals(capability);
    }

    /** Register during mod initialization, before any page using the language is executed. */
    public static synchronized void registerLanguage(LanguageDescriptor language, RuntimeFactory factory) {
        if (language == null || factory == null) throw new IllegalArgumentException("language/factory");
        if (registrations.containsKey(language.id())) throw new IllegalArgumentException("Language id already registered: " + language.id());
        for (String alias : language.aliases()) {
            String existing = aliases.get(alias);
            if (existing != null) throw new IllegalArgumentException("Language alias '" + alias + "' is shared by " + existing + " and " + language.id());
            if (registrations.containsKey(alias)) throw new IllegalArgumentException("Language alias conflicts with registered ID: " + alias);
            if (nativeAlias(alias)) throw new IllegalArgumentException("Language alias '" + alias + "' is already provided by CNPC's JSR223 engine");
        }
        if (aliases.containsKey(LanguageDescriptor.normalize(language.id())))
            throw new IllegalArgumentException("Language id conflicts with alias: " + language.id());
        Map<String, Registration> next = new LinkedHashMap<>(registrations);
        next.put(language.id(), new Registration(language, factory));
        Map<String, String> nextAliases = new LinkedHashMap<>(aliases);
        for (String alias : language.aliases()) nextAliases.put(alias, language.id());
        registrations = Collections.unmodifiableMap(next);
        aliases = Collections.unmodifiableMap(nextAliases);
    }

    public static void unregisterLanguage(String languageId) {
        if (languageId == null) return;
        synchronized (ScriptHostApi.class) {
            Registration removed = registrations.get(languageId);
            if (removed == null) return;
            Map<String, Registration> next = new LinkedHashMap<>(registrations);
            next.remove(languageId);
            Map<String, String> nextAliases = new LinkedHashMap<>(aliases);
            for (String alias : removed.language.aliases()) nextAliases.remove(alias);
            registrations = Collections.unmodifiableMap(next);
            aliases = Collections.unmodifiableMap(nextAliases);
        }
        ScriptRuntimeHost.languageUnregistered(languageId);
    }

    public static synchronized List<LanguageDescriptor> languages() {
        Map<String, LanguageDescriptor> result = new LinkedHashMap<>();
        ScriptController controller = ScriptController.Instance;
        if (controller != null) for (Map.Entry<String, String> entry : controller.languages.entrySet()) {
            String name = entry.getKey();
            String id = "cnpcplus:" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
            result.put(id, new LanguageDescriptor(id, name, "", Collections.singleton(name),
                    Collections.singletonList(entry.getValue()), "", "", ""));
        }
        for (Registration registration : registrations.values()) result.put(registration.language.id(), registration.language);
        List<LanguageDescriptor> languages = new ArrayList<>(result.values());
        languages.sort(Comparator.comparing(LanguageDescriptor::id));
        return Collections.unmodifiableList(languages);
    }

    public static synchronized Optional<LanguageDescriptor> language(String idOrLegacyAlias) {
        Registration registered = registration(idOrLegacyAlias);
        if (registered != null) return Optional.of(registered.language);
        String value = LanguageDescriptor.normalize(idOrLegacyAlias);
        if (value.isEmpty()) return Optional.empty();
        for (LanguageDescriptor descriptor : languages())
            if (descriptor.id().equalsIgnoreCase(value) || descriptor.aliases().contains(value)) return Optional.of(descriptor);
        return Optional.empty();
    }

    public static Optional<InvocationContext> currentInvocation() { return Optional.ofNullable(ScriptRuntimeHost.currentInvocation()); }

    /** Resolve only addon runtimes; native JSR223 languages intentionally remain on CNPC's engine path. */
    public static Optional<String> runtimeLanguageId(String idOrAlias) {
        Registration registration = registration(idOrAlias);
        return registration == null ? Optional.<String>empty() : Optional.of(registration.language.id());
    }

    public static boolean isExplicitLanguageId(String value) {
        return value != null && value.indexOf(':') > 0;
    }

    static synchronized Registration registration(String idOrAlias) {
        if (idOrAlias == null) return null;
        String value = idOrAlias.trim();
        Registration registration = registrations.get(value);
        if (registration != null) return registration;
        String alias = LanguageDescriptor.normalize(value);
        if (nativeAlias(alias)) return null;
        String id = aliases.get(alias);
        return id == null ? null : registrations.get(id);
    }

    public static ScriptRuntime createRuntime(String id, PageContext context) throws Exception {
        Registration registration = registrations.get(id);
        if (registration == null) throw new IllegalStateException("Language provider is unavailable: " + id);
        return registration.factory.create(context);
    }

    private static boolean nativeAlias(String alias) {
        ScriptController controller = ScriptController.Instance;
        if (controller == null) return false;
        for (String name : controller.languages.keySet()) if (LanguageDescriptor.normalize(name).equals(alias)) return true;
        return controller.factories.containsKey(alias);
    }
}
