package bin.cnpcplus.script;

import java.util.*;
import java.util.function.Consumer;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import noppes.npcs.controllers.ScriptController;
import top.cnpcplus.api.script.ScriptLanguageApi.LanguageDescriptor;
import top.cnpcplus.api.script.ScriptPageApi.ResetReason;

/** Internal JSR-223 bridge; registration is separate from controller and engine lifetimes. */
@EventBusSubscriber(modid = "cnpcplus")
public final class ScriptLanguageRuntime {
    private record Registration(LanguageDescriptor descriptor, ScriptEngineFactory factory, Consumer<ScriptEngine> close) {}
    private static volatile Map<String, Registration> registrations = Map.of();
    private static final Map<ScriptEngine, Consumer<ScriptEngine>> engines = new IdentityHashMap<>();
    // Integrated clients edit copies while the logical server ticks its own pages.
    private static final Set<ScriptPageState> active = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean frozen;
    private ScriptLanguageRuntime() {}
    private static String key(String name) { return name == null ? "" : name.strip().toLowerCase(Locale.ROOT); }
    private static Set<String> names(LanguageDescriptor descriptor) {
        var names = new HashSet<String>();
        names.add(key(descriptor.id())); names.add(key(descriptor.engineName()));
        descriptor.aliases().forEach(name -> names.add(key(name)));
        return names;
    }
    public static synchronized void register(LanguageDescriptor descriptor, ScriptEngineFactory factory, Consumer<ScriptEngine> close) {
        if (frozen) throw new IllegalStateException("Language registration closed: register in the addon mod constructor, before common setup/ScriptController construction");
        var claimed = names(descriptor);
        if (claimed.stream().anyMatch(Set.of("javascript", "js", "ecmascript", "nashorn", "javascript es5", "js es5", "ecmascript 5", "ecmascript 5.1")::contains))
            throw new IllegalArgumentException("Language " + descriptor.id() + " conflicts with native ES5");
        for (var entry : registrations.entrySet())
            if (!entry.getKey().equals(descriptor.id()) && !Collections.disjoint(claimed, names(entry.getValue().descriptor)))
                throw new IllegalArgumentException("Language " + descriptor.id() + " conflicts with " + entry.getKey());
        var updated = new TreeMap<>(registrations);
        updated.put(descriptor.id(), new Registration(descriptor, factory, close)); registrations = Map.copyOf(updated);
    }
    private static Registration registration(String name) {
        String key = key(name);
        for (var entry : registrations.values()) if (names(entry.descriptor).contains(key)) return entry;
        return null;
    }
    public static Optional<LanguageDescriptor> find(String name) {
        var entry = registration(name); return entry == null ? Optional.empty() : Optional.of(entry.descriptor);
    }
    public static List<LanguageDescriptor> languages() {
        return registrations.values().stream().map(Registration::descriptor).sorted(Comparator.comparing(LanguageDescriptor::id)).toList();
    }
    public static String extension(String name) {
        var entry = registration(name);
        if (entry != null) return entry.descriptor.extension();
        var controller = ScriptController.Instance;
        if (controller != null) for (var language : controller.languages.entrySet())
            if (key(language.getKey()).equals(key(name))) return language.getValue();
        return "";
    }
    public static synchronized void attach(ScriptController controller) {
        for (var entry : registrations.values()) {
            for (String name : names(entry.descriptor)) {
                var existing = controller.factories.get(name);
                if (existing != null && existing != entry.factory)
                    throw new IllegalStateException("Language " + entry.descriptor.id() + " cannot replace existing engine " + name);
                for (String label : controller.languages.keySet())
                    if (key(label).equals(name) && existing != entry.factory)
                        throw new IllegalStateException("Language " + entry.descriptor.id() + " conflicts with existing label " + label);
            }
        }
        for (var entry : registrations.values()) {
            controller.languages.put(entry.descriptor.engineName(), entry.descriptor.extension());
            controller.factories.put(key(entry.descriptor.engineName()), entry.factory);
        }
        frozen = true;
    }
    public static ScriptEngine create(String name) {
        var entry = Objects.requireNonNull(registration(name));
        ScriptEngine engine = Objects.requireNonNull(entry.factory.getScriptEngine(), "Factory returned null: " + entry.descriptor.id());
        engines.put(engine, entry.close);
        return engine;
    }
    public static void close(ScriptEngine engine) {
        Consumer<ScriptEngine> close = engines.remove(engine);
        if (close == null) return;
        try { close.accept(engine); }
        catch (RuntimeException | LinkageError failure) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus.script").error("Registered script engine close failed: {}", engine.getClass().getName(), failure);
        }
    }
    static void watch(ScriptPageState state) { active.add(state); }
    static void unwatch(ScriptPageState state) { active.remove(state); }
    public static void reload() {
        for (var state : List.copyOf(active)) state.invalidate(ResetReason.RELOAD);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Pre event) {
        for (var state : List.copyOf(active)) {
            state.refresh();
            if (!state.ownerPresent()) state.invalidate(ResetReason.OWNER_REMOVED);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (var state : List.copyOf(active)) state.invalidate(ResetReason.SHUTDOWN);
        for (var engine : List.copyOf(engines.keySet())) close(engine);
    }
}
