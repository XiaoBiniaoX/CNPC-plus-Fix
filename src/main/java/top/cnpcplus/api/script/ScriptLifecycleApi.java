package top.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import top.cnpcplus.script.tools.ScriptScope;

/** Server-side execution-generation handles for addon-owned resources and callbacks. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptLifecycleApi {
    public interface Registration extends AutoCloseable {
        @Override void close();
    }

    public static final class ScriptLifetime {
        private final ScriptPageApi.PageRef page;
        private final long revision;

        private ScriptLifetime(ScriptPageApi.PageRef page, long revision) {
            this.page = page;
            this.revision = revision;
        }

        public boolean isValid() {
            return !stopping && ScriptPageApi.revisionSnapshot(page) == revision;
        }

        public Registration onDispose(Consumer<ScriptPageApi.DisposeReason> cleanup) {
            Objects.requireNonNull(cleanup, "cleanup");
            return register(this, cleanup);
        }
    }

    private static final class Entry implements Registration {
        private final ScriptLifetime lifetime;
        private final Consumer<ScriptPageApi.DisposeReason> cleanup;
        private boolean closed;

        private Entry(ScriptLifetime lifetime, Consumer<ScriptPageApi.DisposeReason> cleanup) {
            this.lifetime = lifetime;
            this.cleanup = cleanup;
        }

        @Override public void close() {
            synchronized (ENTRIES) {
                if (closed) return;
                closed = true;
                List<Entry> entries = ENTRIES.get(lifetime);
                if (entries != null) {
                    entries.remove(this);
                    if (entries.isEmpty()) ENTRIES.remove(lifetime);
                }
                PENDING.removeIf(pending -> pending.entry == this);
            }
        }
    }

    private record Pending(Entry entry, ScriptPageApi.DisposeReason reason) {}
    private static final Map<ScriptLifetime, List<Entry>> ENTRIES = new IdentityHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static volatile boolean stopping;

    private ScriptLifecycleApi() {}

    public static Optional<ScriptLifetime> current() {
        requireServerThread();
        ScriptContainer page = ScriptScope.currentScript();
        IScriptHandler handler = ScriptScope.currentHandler();
        if (page == null || handler == null || !handler.getScripts().contains(page)) return Optional.empty();
        return capture(ScriptPageApi.describe(page, handler).page());
    }

    public static Optional<ScriptLifetime> capture(ScriptPageApi.PageRef page) {
        requireServerThread();
        if (stopping || page == null || !ScriptPageApi.serverOwned(page) || !ScriptPageApi.enabled(page)) return Optional.empty();
        ScriptContainer owner = ScriptPageApi.page(page);
        IScriptHandler handler = ScriptPageApi.handler(page);
        if (owner == null || handler == null || !handler.getScripts().contains(owner)) return Optional.empty();
        long revision = ScriptPageApi.revisionSnapshot(page);
        return revision < 0 ? Optional.empty() : Optional.of(new ScriptLifetime(page, revision));
    }

    public static boolean runWith(ScriptLifetime lifetime, Runnable action) {
        requireServerThread();
        Objects.requireNonNull(action, "action");
        if (lifetime == null || !lifetime.isValid()) return false;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) throw new IllegalStateException("CNPCPLUS.THREAD.MAIN_THREAD_ONLY");
        ScriptContainer page = ScriptPageApi.page(lifetime.page);
        IScriptHandler handler = ScriptPageApi.handler(lifetime.page);
        if (page == null || handler == null || !ScriptPageApi.enabled(lifetime.page) || !lifetime.isValid()) return false;
        ScriptScope.runWith(page, handler, action);
        return true;
    }

    private static Registration register(ScriptLifetime lifetime, Consumer<ScriptPageApi.DisposeReason> cleanup) {
        requireServerThread();
        if (stopping) throw new IllegalStateException("cnpcplus.script.lifecycle_stopped");
        if (!lifetime.isValid()) {
            Entry entry = new Entry(lifetime, cleanup);
            synchronized (ENTRIES) { PENDING.add(new Pending(entry, ScriptPageApi.DisposeReason.OWNER_UNAVAILABLE)); }
            return entry;
        }
        Entry entry = new Entry(lifetime, cleanup);
        synchronized (ENTRIES) { ENTRIES.computeIfAbsent(lifetime, ignored -> new ArrayList<>()).add(entry); }
        return entry;
    }

    static void invalidate(ScriptPageApi.PageRef page, ScriptPageApi.DisposeReason reason) {
        if (!ScriptPageApi.serverOwned(page)) return;
        List<Entry> entries;
        synchronized (ENTRIES) {
            entries = ENTRIES.entrySet().stream().filter(item -> item.getKey().page == page)
                    .flatMap(item -> item.getValue().stream()).toList();
            ENTRIES.entrySet().removeIf(item -> item.getKey().page == page);
            if (!stopping) for (Entry entry : entries) PENDING.add(new Pending(entry, reason));
        }
        if (stopping) for (Entry entry : entries) dispatch(entry, ScriptPageApi.DisposeReason.SERVER_STOPPING);
    }

    @SubscribeEvent
    public static void start(ServerStartingEvent event) { stopping = false; }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || stopping) return;
        List<ScriptPageApi.PageRef> pages;
        synchronized (ENTRIES) {
            pages = ENTRIES.keySet().stream().map(lifetime -> lifetime.page).distinct().toList();
        }
        for (ScriptPageApi.PageRef page : pages) ScriptPageApi.observe(page);
        dispatchPending();
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        stopping = true;
        List<Entry> entries = new ArrayList<>();
        synchronized (ENTRIES) {
            ENTRIES.values().forEach(entries::addAll);
            ENTRIES.clear();
            PENDING.forEach(pending -> entries.add(pending.entry));
            PENDING.clear();
        }
        for (Entry entry : entries) dispatch(entry, ScriptPageApi.DisposeReason.SERVER_STOPPING);
    }

    private static void dispatchPending() {
        List<Pending> pending;
        synchronized (ENTRIES) { pending = List.copyOf(PENDING); PENDING.clear(); }
        for (Pending item : pending) dispatch(item.entry, item.reason);
    }

    private static void dispatch(Entry entry, ScriptPageApi.DisposeReason reason) {
        synchronized (ENTRIES) {
            if (entry.closed) return;
            entry.closed = true;
        }
        try {
            ScriptScope.runUnscoped(() -> entry.cleanup.accept(reason));
        } catch (RuntimeException | LinkageError failure) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Script lifetime cleanup failed", failure);
        }
    }

    private static void requireServerThread() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) throw new IllegalStateException("CNPCPLUS.THREAD.MAIN_THREAD_ONLY");
    }
}
