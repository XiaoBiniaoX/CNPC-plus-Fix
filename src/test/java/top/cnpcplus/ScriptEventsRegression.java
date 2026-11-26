package top.cnpcplus;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.script.ScriptEngineManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.server.ServerLifecycleHooks;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptController;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;

/** Real Forge bus + Nashorn + production page lifetimes, without starting Minecraft. */
public final class ScriptEventsRegression {
    @Cancelable
    public static final class ProbeEvent extends Event {}

    public static void main(String[] args) throws Exception {
        // CNPC's bootstrap needs a loaded mod environment. Keep its page constructor and fields,
        // but omit the static PosZero binding and mod bootstrap. Do not replace lifetime predicates.
        var loader = new ClassLoader(ScriptEventsRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("top.cnpcplus.script.tools.ScriptScope")
                        && !name.startsWith("top.cnpcplus.script.tools.ScriptEvents")
                        && !name.startsWith("top.cnpcplus.script.tools.ScriptScheduler")
                        && !name.startsWith("top.cnpcplus.api.script.ScriptPageApi")
                        && !name.startsWith("top.cnpcplus.api.script.ScriptLifecycleApi")
                        && !name.equals("top.cnpcplus.script.tools.ScriptWorld")
                        && !name.equals("top.cnpcplus.script.tools.ScriptDebug")
                        && !name.equals("noppes.npcs.controllers.ScriptContainer")
                        && !name.equals("noppes.npcs.CustomNpcs")) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                try (var in = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    if (name.equals("noppes.npcs.CustomNpcs") || name.equals("noppes.npcs.controllers.ScriptContainer")) {
                        var node = new ClassNode();
                        new ClassReader(bytes).accept(node, 0);
                        var init = node.methods.stream().filter(method -> method.name.equals("<clinit>")).findFirst().orElseThrow();
                        init.instructions.clear();
                        init.tryCatchBlocks.clear();
                        if (init.localVariables != null) init.localVariables.clear();
                        if (name.equals("noppes.npcs.CustomNpcs")) {
                            init.instructions.add(new InsnNode(Opcodes.ICONST_1));
                            init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, "EnableScripting", "Z"));
                        }
                        init.instructions.add(new InsnNode(Opcodes.RETURN));
                        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        node.accept(writer);
                        bytes = writer.toByteArray();
                    }
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException error) {
                    throw new ClassNotFoundException(name, error);
                }
            }
        };
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        // Only thread identity is used: no world, server constructor, or game loop is simulated.
        var server = (MinecraftServer) unsafe.allocateInstance(DedicatedServer.class);
        var serverThread = MinecraftServer.class.getDeclaredField("serverThread");
        serverThread.setAccessible(true);
        serverThread.set(server, Thread.currentThread());
        var currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        Object previousServer = currentServer.get(null);
        var previousController = ScriptController.Instance;
        boolean previousHasStart = ScriptController.HasStart;
        currentServer.set(null, server);
        ScriptController.Instance = (ScriptController) unsafe.allocateInstance(ScriptController.class);
        ScriptController.class.getField("executorProviders").set(ScriptController.Instance,
                new java.util.HashMap<>(Map.of("ecmascript", (Supplier<?>) () -> null)));
        MinecraftForge.EVENT_BUS.start();
        var busListeners = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("listeners");
        busListeners.setAccessible(true);
        int initialListeners = ((Map<?, ?>) busListeners.get(MinecraftForge.EVENT_BUS)).size();
        var type = Class.forName("top.cnpcplus.script.tools.ScriptEvents", true, loader);
        try {
            var pageType = Class.forName("noppes.npcs.controllers.ScriptContainer", true, loader);
            var scope = Class.forName("top.cnpcplus.script.tools.ScriptScope", true, loader);
            var run = scope.getMethod("run", pageType, IScriptHandler.class, Object.class, Supplier.class);
            var pages = new ArrayList<Object>();
            var enabled = new boolean[] {true};
            var language = new String[] {"ECMAScript"};
            var handler = (IScriptHandler) Proxy.newProxyInstance(loader, new Class<?>[] {IScriptHandler.class}, (proxy, method, values) -> switch (method.getName()) {
                case "getEnabled" -> enabled[0];
                case "getScripts" -> pages;
                case "getLanguage" -> language[0];
                case "isClient" -> false;
                case "getConsoleText" -> new TreeMap<>();
                case "noticeString" -> "events regression";
                default -> null;
            });
            Object first = pageType.getConstructor(IScriptHandler.class).newInstance(handler);
            Object second = pageType.getConstructor(IScriptHandler.class).newInstance(handler);
            for (Object page : List.of(first, second)) {
                pageType.getField("scripts").set(page, new ArrayList<String>());
                pages.add(page);
            }
            var engine = new ScriptEngineManager(loader).getEngineByName("nashorn");
            engine.put("events", type.getConstructor().newInstance());
            var scheduler = Class.forName("top.cnpcplus.script.tools.ScriptScheduler", true, loader);
            engine.put("Scheduler", scheduler.getConstructor().newInstance());
            engine.put("bus", MinecraftForge.EVENT_BUS);
            engine.put("probe", new ProbeEvent());
            engine.put("newProbe", (Supplier<ProbeEvent>) ProbeEvent::new);
            engine.put("eventClass", ProbeEvent.class.getName());
            engine.put("callbackAssertions", new ArrayList<String>());
            // Two real callers share the same scope runner (normal page and cross-page event dispatch).
            java.util.function.BiConsumer<Object, String> eval = (page, code) -> {
                try {
                    run.invoke(null, page, handler, new Object(), (Supplier<String>) () -> {
                        try { engine.eval(code); return ""; }
                        catch (javax.script.ScriptException error) { throw new AssertionError(error); }
                    });
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            };
            try {
                engine.eval("events.on(eventClass,function(event){})");
                throw new AssertionError("subscription accepted without a script page");
            } catch (IllegalStateException expected) {
                check(expected.getMessage().equals("CNPCPLUS.EVENTS.SCRIPT_CONTEXT_REQUIRED"), "wrong context failure");
            }
            engine.eval("var hits=0, otherHits=0;");
            eval.accept(first, "var listener=events.on(eventClass,function(event){hits++;callbackAssertions.add(String(events.has(listener)))});"
                    + "if(!events.has(listener)||events.list().size()!==1)throw 'registration failed';");
            eval.accept(second, "var other=events.on(eventClass,function(event){otherHits++});"
                    + "if(events.has(listener)||events.off(listener)||events.list().size()!==1)throw 'page isolation failed';");
            MinecraftForge.EVENT_BUS.post(new ProbeEvent());
            check(((Number) engine.get("hits")).intValue() == 1 && ((Number) engine.get("otherHits")).intValue() == 1,
                    "dispatch failed: hits=" + engine.get("hits") + " other=" + engine.get("otherHits")
                            + " states=" + engine.eval("listener.getState()+','+other.getState()") + " serverThread=" + server.isSameThread());
            check(engine.eval("String(callbackAssertions)").equals("[true]"), "callback lost its registering page");
            // Deletion must unregister even if the next event arrives before the cleanup tick.
            pages.remove(first);
            MinecraftForge.EVENT_BUS.post(new ProbeEvent());
            check(((Number) engine.get("hits")).intValue() == 1 && ((Number) engine.get("otherHits")).intValue() == 2, "deleted page still ran, or affected sibling page");
            check(engine.eval("String(listener.getState())").equals("UNSUBSCRIBED"), "deleted page not unregistered");
            pages.add(first);
            eval.accept(second, "if(!events.off(other)||events.off(other)||events.has(other)||events.off(null))throw 'off failed';");
            // The event is emitted while a DIFFERENT page is running: offAll must use the callback's page.
            eval.accept(first, "listener=events.on(eventClass,function(event){if(events.offAll()!==1)throw 'callback page mismatch'});");
            eval.accept(second, "other=events.on(eventClass,function(event){otherHits++});bus.post(probe);"
                    + "if(!events.has(other)||events.list().size()!==1)throw 'cross-page callback cleared caller';");
            eval.accept(second, "if(events.offAll()!==1||events.list().size()!==0)throw 'offAll failed';");
            check(engine.eval("String(listener.getState())").equals("UNSUBSCRIBED"), "self-unsubscribe failed");
            // The next START tick removes listeners even when no subscribed event is ever posted again.
            eval.accept(first, "listener=events.on(eventClass,function(event){hits++});");
            pages.remove(first);
            tick(type);
            check(engine.eval("String(listener.getState())").equals("UNSUBSCRIBED"), "idle deleted page not cleaned on tick");
            pages.add(first);
            // CNPC's NBT save replaces ALL containers, including unchanged sibling pages.
            // Record that limit separately from removal of one container in the same handler.
            eval.accept(first, "listener=events.on(eventClass,function(event){hits++});");
            eval.accept(second, "other=events.on(eventClass,function(event){otherHits++});");
            pages.clear();
            tick(type);
            check(engine.eval("listener.getState()+','+other.getState()").equals("UNSUBSCRIBED,UNSUBSCRIBED"),
                    "replaced page containers kept old subscriptions");
            pages.addAll(List.of(first, second));
            // A task group adds its own lifetime but must retain the page identity for later subscriptions.
            eval.accept(first, "var group=Scheduler.createGroup();group.runLater(1,function(){listener=events.on(eventClass,function(event){hits++})});");
            tick(scheduler);
            eval.accept(first, "if(!events.has(listener))throw 'scheduled callback lost registering page';events.offAll();");
            // Content, links, handler enable/language and global reload use production ScriptScope predicates.
            for (String change : List.of("content", "links", "disabled", "language", "reload")) {
                enabled[0] = true;
                eval.accept(first, "listener=events.on(eventClass,function(event){hits++});");
                switch (change) {
                    case "content" -> pageType.getField("script").set(first, "updated");
                    case "links" -> pageType.getField("scripts").set(first, List.of("new-link.js"));
                    case "disabled" -> enabled[0] = false;
                    case "language" -> language[0] = "other";
                    case "reload" -> ScriptController.Instance.lastLoaded++;
                }
                tick(type);
                check(engine.eval("String(listener.getState())").equals("UNSUBSCRIBED"), change + " not cleaned");
            }
            enabled[0] = true;
            language[0] = "ECMAScript";
            eval.accept(first, "var order=[];var high=events.on(eventClass,'HIGHEST',false,function(event){order.push('high');event.setCanceled(true)});"
                    + "var normal=events.on(eventClass,function(event){order.push('normal')});"
                    + "var low=events.on(eventClass,'LOWEST',true,function(event){order.push('low')});");
            check(MinecraftForge.EVENT_BUS.post(new ProbeEvent()), "synchronous cancellation not propagated");
            check(engine.eval("order.join(',')").equals("high,low"), "priority/receiveCanceled failed: " + engine.eval("order.join(',')"));
            eval.accept(first, "if(events.offAll()!==3)throw 'count mismatch';");
            eval.accept(first, "var recursiveHits=0;listener=events.on(eventClass,function(event){recursiveHits++;bus.post(newProbe.get())});");
            MinecraftForge.EVENT_BUS.post(new ProbeEvent());
            check(((Number) engine.get("recursiveHits")).intValue() == 1 && engine.eval("listener.isActive()").equals(true), "recursive callback not bounded");
            // Worker/client thread posts must neither run nor enqueue server script callbacks.
            var worker = new Thread(() -> MinecraftForge.EVENT_BUS.post(new ProbeEvent()), "events-test-worker");
            worker.start();
            worker.join();
            check(((Number) engine.get("recursiveHits")).intValue() == 1, "worker event entered script");
            eval.accept(first, "events.offAll();var bad=events.on(eventClass,function(event){throw new Error('expected regression failure')});"
                    + "listener=events.on(eventClass,function(event){hits++});");
            int before = ((Number) engine.get("hits")).intValue();
            MinecraftForge.EVENT_BUS.post(new ProbeEvent());
            check(engine.eval("String(bad.getState())").equals("FAILED") && engine.eval("bad.getFailure()!==null").equals(true), "callback failure not contained");
            check(((Number) engine.get("hits")).intValue() == before + 1, "failed callback interrupted other listeners");
            MinecraftForge.EVENT_BUS.post(new ProbeEvent());
            check(((Number) engine.get("hits")).intValue() == before + 2 && engine.eval("String(bad.getState())").equals("FAILED"),
                    "failed subscription was invoked again or blocked a healthy listener");
            eval.accept(first, "if(events.isAvailable('missing.mod.Event'))throw 'missing mod detected';"
                    + "var rejected=false;try{events.on('missing.mod.Event',function(e){})}catch(e){rejected=true}"
                    + "if(!rejected)throw 'missing mod accepted';");
            for (String unsupported : List.of("java.lang.String", "net.minecraftforge.eventbus.api.GenericEvent",
                    "net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent")) {
                engine.put("unsupported", unsupported);
                eval.accept(first, "var rejected=false;try{events.on(unsupported,function(e){})}catch(e){rejected=true}"
                        + "if(!rejected)throw 'unsupported event accepted';");
            }
            type.getMethod("stop", ServerStoppedEvent.class).invoke(null, new Object[] {null});
            check(engine.eval("String(listener.getState())").equals("UNSUBSCRIBED"), "server stop did not unregister");
            var active = type.getDeclaredField("ACTIVE");
            active.setAccessible(true);
            check(((Map<?, ?>) active.get(null)).isEmpty(), "listener registry leaked");
            check(((Map<?, ?>) busListeners.get(MinecraftForge.EVENT_BUS)).size() == initialListeners,
                    "Forge bus retained unregistered Consumers");
            publicLifetimes(loader, run, scope, handler, pages, enabled, first);
            System.out.println("SCRIPT_EVENTS_NASHORN_FORGE_BUS_PAGE_LIFECYCLE=PASS");
        } finally {
            type.getMethod("stop", ServerStoppedEvent.class).invoke(null, new Object[] {null});
            currentServer.set(null, previousServer);
            ScriptController.Instance = previousController;
            ScriptController.HasStart = previousHasStart;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void publicLifetimes(ClassLoader loader, java.lang.reflect.Method scopeRun, Class<?> scope,
                                       IScriptHandler handler, ArrayList<Object> pages, boolean[] enabled,
                                       Object page) throws Exception {
        Class<?> pageType = page.getClass();
        Class<?> pageApi = Class.forName("top.cnpcplus.api.script.ScriptPageApi", true, loader);
        Class<?> lifecycle = Class.forName("top.cnpcplus.api.script.ScriptLifecycleApi", true, loader);
        Class<?> carrierType = Class.forName("top.cnpcplus.api.script.ScriptPageApi$Carrier", true, loader);
        Class<?> reasonType = Class.forName("top.cnpcplus.api.script.ScriptPageApi$DisposeReason", true, loader);
        Class<?> refType = Class.forName("top.cnpcplus.api.script.ScriptPageApi$PageRef", true, loader);
        Class<?> lifetimeType = Class.forName("top.cnpcplus.api.script.ScriptLifecycleApi$ScriptLifetime", true, loader);
        var describe = pageApi.getMethod("describe", pageType, IScriptHandler.class, carrierType, List.class);
        var observe = pageApi.getDeclaredMethod("observe", refType);
        observe.setAccessible(true);
        var current = lifecycle.getMethod("current");
        var runWith = lifecycle.getMethod("runWith", lifetimeType, Runnable.class);
        Object npc = Enum.valueOf((Class<? extends Enum>) carrierType, "NPC");
        Object descriptor = describe.invoke(null, page, handler, npc, List.of("interact"));
        Object ref = descriptor.getClass().getMethod("page").invoke(descriptor);
        Object lifetime = capture(scopeRun, page, handler, current);
        check((boolean) lifetimeType.getMethod("isValid").invoke(lifetime), "new public lifetime was invalid");
        boolean[] scoped = {false};
        check((boolean) runWith.invoke(null, lifetime, (Runnable) () -> {
            try { scoped[0] = scope.getMethod("currentScript").invoke(null) == page; }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }) && scoped[0], "runWith did not restore page ownership");

        var disposed = new java.util.concurrent.atomic.AtomicReference<String>();
        boolean[] cleanupUnscoped = {false};
        java.util.function.Consumer<Object> cleanup = reason -> {
            disposed.set(reason.toString());
            try { cleanupUnscoped[0] = scope.getMethod("currentScript").invoke(null) == null; }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        };
        lifetimeType.getMethod("onDispose", java.util.function.Consumer.class).invoke(lifetime, cleanup);
        pageType.getField("script").set(page, "lifetime-source-change");
        Object sourceChanged = Enum.valueOf((Class<? extends Enum>) reasonType, "SOURCE_CHANGED");
        pageApi.getMethod("invalidate", refType, reasonType).invoke(null, ref, sourceChanged);
        lifecycleTick(lifecycle);
        check(!(boolean) lifetimeType.getMethod("isValid").invoke(lifetime)
                        && "SOURCE_CHANGED".equals(disposed.get()) && cleanupUnscoped[0],
                "source invalidation did not expire lifetime or isolate cleanup");

        Object afterEdit = capture(scopeRun, page, handler, current);
        disposed.set(null);
        lifetimeType.getMethod("onDispose", java.util.function.Consumer.class).invoke(afterEdit, cleanup);
        pages.remove(page);
        observe.invoke(null, ref);
        lifecycleTick(lifecycle);
        check("REMOVED".equals(disposed.get()), "removed page cleanup reason changed");

        pages.add(page);
        descriptor = describe.invoke(null, page, handler, npc, List.of("interact"));
        ref = descriptor.getClass().getMethod("page").invoke(descriptor);
        Object beforeDisable = capture(scopeRun, page, handler, current);
        disposed.set(null);
        lifetimeType.getMethod("onDispose", java.util.function.Consumer.class).invoke(beforeDisable, cleanup);
        enabled[0] = false;
        observe.invoke(null, ref);
        lifecycleTick(lifecycle);
        check("DISABLED".equals(disposed.get()), "disabled page cleanup reason changed");

        enabled[0] = true;
        descriptor = describe.invoke(null, page, handler, npc, List.of("interact"));
        ref = descriptor.getClass().getMethod("page").invoke(descriptor);
        Object beforeGlobalDisable = capture(scopeRun, page, handler, current);
        disposed.set(null);
        lifetimeType.getMethod("onDispose", java.util.function.Consumer.class).invoke(beforeGlobalDisable, cleanup);
        var scriptingToggle = Class.forName("noppes.npcs.CustomNpcs", true, loader).getField("EnableScripting");
        scriptingToggle.setBoolean(null, false);
        observe.invoke(null, ref);
        lifecycleTick(lifecycle);
        check("DISABLED".equals(disposed.get()), "global script disable did not dispose page lifetime");

        scriptingToggle.setBoolean(null, true);
        describe.invoke(null, page, handler, npc, List.of("interact"));
        Object beforeReload = capture(scopeRun, page, handler, current);
        disposed.set(null);
        lifetimeType.getMethod("onDispose", java.util.function.Consumer.class).invoke(beforeReload, cleanup);
        ScriptController.Instance.lastLoaded++;
        observe.invoke(null, ref);
        lifecycleTick(lifecycle);
        check("RELOADED".equals(disposed.get()), "controller reload did not dispose page lifetime");

        // PlayerScriptData uses a separate template generation from the global controller reload.
        Class<?> playerData = Class.forName("noppes.npcs.controllers.data.PlayerScriptData", true, loader);
        var playerConstructor = playerData.getConstructor(net.minecraft.world.entity.player.Player.class);
        Object globalPlayerData = playerConstructor.newInstance((Object) null);
        Object playerHandler = playerConstructor.newInstance((Object) null);
        playerData.getMethod("setEnabled", boolean.class).invoke(globalPlayerData, true);
        playerData.getMethod("setEnabled", boolean.class).invoke(playerHandler, true);
        ScriptController.Instance.playerScripts = (noppes.npcs.controllers.data.PlayerScriptData) globalPlayerData;
        ScriptController.HasStart = true;
        Object playerPage = pageType.getConstructor(IScriptHandler.class).newInstance(playerHandler);
        pageType.getField("script").set(playerPage, "player-template-source");
        ((List<Object>) playerData.getMethod("getScripts").invoke(playerHandler)).add(playerPage);
        Object playerDescriptor = describe.invoke(null, playerPage, playerHandler,
                Enum.valueOf((Class<? extends Enum>) carrierType, "PLAYER"), List.of("login"));
        Object playerRef = playerDescriptor.getClass().getMethod("page").invoke(playerDescriptor);
        Object playerLifetime = capture(scopeRun, playerPage, (IScriptHandler) playerHandler, current);
        disposed.set(null);
        lifetimeType.getMethod("onDispose", java.util.function.Consumer.class).invoke(playerLifetime, cleanup);
        ScriptController.Instance.lastPlayerUpdate++;
        observe.invoke(null, playerRef);
        lifecycleTick(lifecycle);
        check("RELOADED".equals(disposed.get()), "player template generation did not dispose lifetime");

        Object atShutdown = capture(scopeRun, playerPage, (IScriptHandler) playerHandler, current);
        disposed.set(null);
        lifetimeType.getMethod("onDispose", java.util.function.Consumer.class).invoke(atShutdown, cleanup);
        pageApi.getMethod("stop", ServerStoppedEvent.class).invoke(null, new Object[] {null});
        lifecycle.getMethod("stop", ServerStoppedEvent.class).invoke(null, new Object[] {null});
        check("SERVER_STOPPING".equals(disposed.get())
                        && !(boolean) lifetimeType.getMethod("isValid").invoke(atShutdown)
                        && !(boolean) runWith.invoke(null, atShutdown, (Runnable) () -> { throw new AssertionError("stale lifetime ran"); }),
                "server shutdown did not invalidate and clean up lifetimes");
        lifecycle.getMethod("start", net.minecraftforge.event.server.ServerStartingEvent.class).invoke(null, new Object[] {null});
    }

    private static Object capture(java.lang.reflect.Method scopeRun, Object page, IScriptHandler handler,
                                  java.lang.reflect.Method current) throws Exception {
        Object[] result = new Object[1];
        scopeRun.invoke(null, page, handler, new Object(), (Supplier<String>) () -> {
            try { result[0] = current.invoke(null); }
            catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
            return "";
        });
        return ((java.util.Optional<?>) result[0]).orElseThrow(() -> new AssertionError("script lifetime was unavailable"));
    }

    private static void lifecycleTick(Class<?> lifecycle) throws Exception {
        lifecycle.getMethod("tick", TickEvent.ServerTickEvent.class).invoke(null,
                new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, null));
    }

    private static void tick(Class<?> type) throws Exception {
        type.getMethod("tick", TickEvent.ServerTickEvent.class).invoke(null,
                new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, null));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
