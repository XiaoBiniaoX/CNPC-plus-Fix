package top.cnpcplus.script.tools;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.GenericEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import noppes.npcs.controllers.ScriptContainer;

/** Script-scoped, synchronous listeners on the Forge gameplay bus. No third-party mod dependency. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptEvents {
    private static final Map<Long, Subscription> ACTIVE = new LinkedHashMap<>();
    private static long lastId;

    public boolean isAvailable(String eventClass) {
        ScriptWorld.server();
        try {
            eventType(eventClass);
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    public Subscription on(String eventClass, Consumer<Event> callback) {
        return on(eventClass, "NORMAL", false, callback);
    }

    @SuppressWarnings("unchecked")
    public Subscription on(String eventClass, String priority, boolean receiveCanceled, Consumer<Event> callback) {
        ScriptWorld.server();
        Objects.requireNonNull(callback, "callback");
        EventPriority order = EventPriority.valueOf(priority.toUpperCase(Locale.ROOT));
        Class<? extends Event> type;
        try {
            type = eventType(eventClass);
        } catch (ClassNotFoundException | LinkageError unavailable) {
            throw new IllegalArgumentException("CNPCPLUS.EVENTS.CLASS_UNAVAILABLE: " + eventClass, unavailable);
        }
        var subscription = new Subscription(++lastId, eventClass, ScriptScope.script(), callback, ScriptScope.current());
        MinecraftForge.EVENT_BUS.addListener(order, receiveCanceled, (Class<Event>) (Class<?>) type, subscription.listener);
        ACTIVE.put(subscription.id, subscription);
        return subscription;
    }

    public boolean off(Subscription subscription) {
        if (!has(subscription)) return false;
        subscription.finish("UNSUBSCRIBED");
        return true;
    }

    public int offAll() {
        var subscriptions = list();
        for (Subscription subscription : subscriptions) subscription.finish("UNSUBSCRIBED");
        return subscriptions.size();
    }

    public boolean has(Subscription subscription) {
        ScriptWorld.server();
        var script = ScriptScope.script();
        return subscription != null && subscription.script.get() == script && subscription.callback != null
                && subscription.owner.getAsBoolean();
    }

    public List<Subscription> list() {
        ScriptWorld.server();
        var script = ScriptScope.script();
        return ACTIVE.values().stream().filter(subscription -> subscription.script.get() == script
                && subscription.owner.getAsBoolean()).toList();
    }

    private static Class<? extends Event> eventType(String name) throws ClassNotFoundException {
        Class<?> type = Class.forName(name, false, ScriptEvents.class.getClassLoader());
        if (!Event.class.isAssignableFrom(type) || IModBusEvent.class.isAssignableFrom(type)
                || GenericEvent.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Expected a non-generic Forge gameplay Event: " + name);
        }
        return type.asSubclass(Event.class);
    }

    public static final class Subscription {
        private final long id;
        private final String eventClass;
        private final WeakReference<ScriptContainer> script;
        // Keep the exact Consumer registered with Forge: unregister uses its identity.
        private final Consumer<Event> listener = this::dispatch;
        private Consumer<Event> callback;
        private BooleanSupplier owner;
        private String state = "ACTIVE";
        private OperationResult.Diagnostic failure;
        private boolean dispatching;

        private Subscription(long id, String eventClass, ScriptContainer script, Consumer<Event> callback, BooleanSupplier owner) {
            this.id = id;
            this.eventClass = eventClass;
            this.script = new WeakReference<>(script);
            this.callback = callback;
            this.owner = owner;
        }

        public long getId() { return id; }
        public String getEventClass() { return eventClass; }
        public String getState() { ScriptWorld.server(); return state; }
        public boolean isActive() { return "ACTIVE".equals(getState()); }
        public OperationResult.Diagnostic getFailure() { ScriptWorld.server(); return failure; }
        private void dispatch(Event event) {
            // Forge also posts client/worker events. Never invoke a server script there, or defer a live event.
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || !server.isSameThread()) return;
            if (callback == null || dispatching) return;
            if (!owner.getAsBoolean()) { finish("UNSUBSCRIBED"); return; }
            dispatching = true;
            try {
                ScriptScope.callback(owner, () -> callback.accept(event));
            } catch (RuntimeException error) {
                failure = OperationResult.failure("EVENTS", "CALLBACK_FAILED").getDiagnostic();
                ScriptDebug.record(failure);
                finish("FAILED");
                org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Script event subscription {} ({}) failed", id, eventClass, error);
            } finally {
                dispatching = false;
            }
        }

        private void finish(String result) {
            MinecraftForge.EVENT_BUS.unregister(listener);
            ACTIVE.remove(id);
            state = result;
            callback = null;
            owner = null;
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (Subscription subscription : List.copyOf(ACTIVE.values())) {
            if (subscription.owner != null && !subscription.owner.getAsBoolean()) subscription.finish("UNSUBSCRIBED");
        }
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for (Subscription subscription : List.copyOf(ACTIVE.values())) subscription.finish("UNSUBSCRIBED");
    }
}
