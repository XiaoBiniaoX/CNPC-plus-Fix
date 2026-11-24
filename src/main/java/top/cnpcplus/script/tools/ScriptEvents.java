package top.cnpcplus.script.tools;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.script.ScriptEngine;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** One instance per evaluated script page; only server-thread game-bus events are delivered. */
@EventBusSubscriber(modid = "cnpcplus")
public final class ScriptEvents {
    public record Subscription(long id, String eventClass) {}
    private static final Set<ScriptEvents> ACTIVE = new LinkedHashSet<>();
    private static long lastId;
    private final Map<Long, Listener> listeners = new LinkedHashMap<>();
    private final BooleanSupplier owner;

    ScriptEvents(BooleanSupplier owner) { this.owner = owner; }

    /** Called immediately before CNPC evaluates a page, including re-evaluation on reload. */
    public static void bind(ScriptEngine engine) {
        if (engine.get("events") instanceof ScriptEvents previous) previous.clear();
        engine.put("events", new ScriptEvents(ScriptScope.current()));
    }

    @SuppressWarnings("unchecked")
    public long on(String eventClass, Consumer<Object> callback) {
        ScriptWorld.server();
        Objects.requireNonNull(callback, "Event callback");
        if (!owner.getAsBoolean()) throw new IllegalStateException("CNPCPLUS.SCRIPT.UNLOADED");
        Class<?> type;
        try {
            type = Class.forName(Objects.requireNonNull(eventClass, "Event class"), false, ScriptEvents.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError error) {
            throw new IllegalArgumentException("Event class not found: " + eventClass, error);
        }
        if (!Event.class.isAssignableFrom(type) || IModBusEvent.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers()))
            throw new IllegalArgumentException("Expected a NeoForge game-bus Event: " + eventClass);
        Listener listener = new Listener(++lastId, eventClass, callback);
        NeoForge.EVENT_BUS.addListener((Class<Event>) type, listener);
        listeners.put(listener.info.id, listener);
        ACTIVE.add(this);
        return listener.info.id;
    }

    public boolean off(long id) { ScriptWorld.server(); return remove(id); }
    public int offAll() { ScriptWorld.server(); return clear(); }
    public boolean has(long id) {
        ScriptWorld.server();
        if (!owner.getAsBoolean()) clear();
        return listeners.containsKey(id);
    }
    public List<Subscription> list() {
        ScriptWorld.server();
        if (!owner.getAsBoolean()) clear();
        return listeners.values().stream().map(listener -> listener.info).toList();
    }

    private boolean remove(long id) {
        Listener listener = listeners.remove(id);
        if (listener == null) return false;
        NeoForge.EVENT_BUS.unregister(listener);
        if (listeners.isEmpty()) ACTIVE.remove(this);
        return true;
    }

    private int clear() {
        int count = listeners.size();
        for (Listener listener : listeners.values()) NeoForge.EVENT_BUS.unregister(listener);
        listeners.clear();
        ACTIVE.remove(this);
        return count;
    }

    private final class Listener implements Consumer<Event> {
        private final Subscription info;
        private final Consumer<Object> callback;
        private Listener(long id, String eventClass, Consumer<Object> callback) {
            this.info = new Subscription(id, eventClass);
            this.callback = callback;
        }

        @Override public void accept(Event event) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || !server.isSameThread() || !listeners.containsKey(info.id)) return;
            if (!owner.getAsBoolean()) { clear(); return; }
            try {
                ScriptScope.callback(owner, () -> callback.accept(event));
            } catch (RuntimeException error) {
                remove(info.id);
                ScriptDebug.record(OperationResult.failure("EVENTS", "CALLBACK_FAILED").getDiagnostic());
                org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Script event listener {} ({}) failed and was removed", info.id, info.eventClass, error);
            }
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Pre event) {
        for (ScriptEvents page : List.copyOf(ACTIVE)) if (!page.owner.getAsBoolean()) page.clear();
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for (ScriptEvents page : List.copyOf(ACTIVE)) page.clear();
    }
}
