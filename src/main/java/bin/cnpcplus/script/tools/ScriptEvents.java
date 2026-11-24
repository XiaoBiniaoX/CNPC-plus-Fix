package bin.cnpcplus.script.tools;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.controllers.*;
import noppes.npcs.controllers.data.PlayerScriptData;

/** Synchronous, page-owned listeners; no optional mod class is linked from common code. */
public final class ScriptEvents {
    private static final Map<Long, Subscription> ACTIVE = new LinkedHashMap<>();
    private static long lastId;

    public boolean isAvailable(String eventClass) {
        ScriptWorld.server();
        try { eventType(eventClass); return true; }
        catch (ClassNotFoundException | LinkageError | IllegalArgumentException unavailable) { return false; }
    }
    public Subscription on(String eventClass, Consumer<Event> callback) { return on(eventClass, false, callback); }
    public Subscription on(String eventClass, boolean receiveCanceled, Consumer<Event> callback) {
        ScriptWorld.server();
        Objects.requireNonNull(callback, "callback");
        ScriptContainer script = ScriptScope.script();
        Class<? extends Event> type;
        try { type = eventType(eventClass); }
        catch (ClassNotFoundException | LinkageError unavailable) {
            throw new IllegalArgumentException("CNPCPLUS.EVENTS.CLASS_UNAVAILABLE: " + eventClass, unavailable);
        }
        Subscription subscription = new Subscription(++lastId, type, script, ScriptScope.handler(), callback,
                ScriptScope.current(), receiveCanceled);
        ACTIVE.put(subscription.id, subscription);
        return subscription;
    }
    public boolean off(Subscription subscription) {
        if (!has(subscription)) return false;
        subscription.finish("UNSUBSCRIBED"); return true;
    }
    public int offAll() {
        List<Subscription> subscriptions = list();
        for (Subscription subscription : subscriptions) subscription.finish("UNSUBSCRIBED");
        return subscriptions.size();
    }
    public boolean has(Subscription subscription) {
        ScriptWorld.server(); ScriptContainer script = ScriptScope.script();
        return subscription != null && subscription.script.get() == script && subscription.callback != null;
    }
    public List<Subscription> list() {
        ScriptWorld.server(); ScriptContainer script = ScriptScope.script();
        List<Subscription> result = new ArrayList<>();
        for (Subscription subscription : ACTIVE.values()) if (subscription.script.get() == script) result.add(subscription);
        return result;
    }
    private static Class<? extends Event> eventType(String name) throws ClassNotFoundException {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Event class name is required");
        Class<?> type = Class.forName(name, false, ScriptEvents.class.getClassLoader());
        if (!Event.class.isAssignableFrom(type) || IGenericEvent.class.isAssignableFrom(type))
            throw new IllegalArgumentException("Expected a non-generic Forge 1.12.2 Event: " + name);
        return type.asSubclass(Event.class);
    }
    public static final class Subscription {
        private final long id;
        private final Class<? extends Event> type;
        private final WeakReference<ScriptContainer> script;
        private final WeakReference<IScriptHandler> handler;
        private final boolean receiveCanceled;
        private Consumer<Event> callback;
        private BooleanSupplier owner;
        private String state = "ACTIVE";
        private OperationResult.Diagnostic failure;
        private Subscription(long id, Class<? extends Event> type, ScriptContainer script, IScriptHandler handler,
                Consumer<Event> callback, BooleanSupplier owner, boolean receiveCanceled) {
            this.id = id; this.type = type; this.script = new WeakReference<>(script); this.handler = new WeakReference<>(handler);
            this.callback = callback; this.owner = owner; this.receiveCanceled = receiveCanceled;
        }
        public long getId() { return id; }
        public String getEventClass() { return type.getName(); }
        public String getState() { ScriptWorld.server(); return state; }
        public boolean isActive() { return "ACTIVE".equals(getState()); }
        public OperationResult.Diagnostic getFailure() { ScriptWorld.server(); return failure; }
        private void dispatch(Event event) {
            if (callback == null || !type.isInstance(event)) return;
            if (!owner.getAsBoolean()) { finish("UNSUBSCRIBED"); return; }
            if (!receiveCanceled && event.isCanceled()) return;
            try { ScriptScope.callback(owner, () -> callback.accept(event)); }
            catch (RuntimeException error) {
                failure = OperationResult.failure("EVENTS", "CALLBACK_FAILED").getDiagnostic();
                finish("FAILED");
                ScriptContainer page = script.get();
                if (page != null) page.appandConsole("events " + id + " (" + type.getName() + "): " + error);
                bin.cnpcplus.CnpcPlus.LOGGER.warn("Script event subscription {} ({}) failed", id, type.getName(), error);
            }
        }
        private void finish(String result) {
            ACTIVE.remove(id); state = result; callback = null; owner = null;
        }
    }
    public static void init() {
        MinecraftForge.EVENT_BUS.register(ScriptEvents.class);
        NpcAPI.Instance().events().register(ScriptEvents.class);
    }
    @SubscribeEvent(receiveCanceled = true) public static void event(Event event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        // Never defer a live/cancelable event, or enter a server script from a client/worker thread.
        if (server == null || !server.isCallingFromMinecraftThread() || ACTIVE.isEmpty()) return;
        // ponytail: one native bridge; linear scan suits script-scale listener counts, not thousands of global listeners.
        for (Subscription subscription : new ArrayList<>(ACTIVE.values())) subscription.dispatch(event);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (Subscription subscription : new ArrayList<>(ACTIVE.values()))
            if (!subscription.owner.getAsBoolean()) subscription.finish("UNSUBSCRIBED");
    }
    /** The common NBT page replacement point, before the old containers are discarded. */
    public static void release(IScriptHandler handler) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || !server.isCallingFromMinecraftThread()) return;
        boolean playerTemplate = ScriptController.Instance != null && handler == ScriptController.Instance.playerScripts;
        for (Subscription subscription : new ArrayList<>(ACTIVE.values()))
            if (subscription.handler.get() == handler || playerTemplate && subscription.handler.get() instanceof PlayerScriptData)
                subscription.finish("UNSUBSCRIBED");
    }
    public static void release(ScriptContainer script) {
        if (!serverThread()) return;
        for (Subscription subscription : new ArrayList<>(ACTIVE.values()))
            if (subscription.script.get() == script) subscription.finish("UNSUBSCRIBED");
    }
    private static boolean serverThread() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null && server.isCallingFromMinecraftThread();
    }
    public static void stop() {
        for (Subscription subscription : new ArrayList<>(ACTIVE.values())) subscription.finish("UNSUBSCRIBED");
    }
}
