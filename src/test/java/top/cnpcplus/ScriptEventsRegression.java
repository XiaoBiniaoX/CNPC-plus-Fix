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
        currentServer.set(null, server);
        ScriptController.Instance = (ScriptController) unsafe.allocateInstance(ScriptController.class);
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
            System.out.println("SCRIPT_EVENTS_NASHORN_FORGE_BUS_PAGE_LIFECYCLE=PASS");
        } finally {
            type.getMethod("stop", ServerStoppedEvent.class).invoke(null, new Object[] {null});
            currentServer.set(null, previousServer);
            ScriptController.Instance = previousController;
        }
    }

    private static void tick(Class<?> type) throws Exception {
        type.getMethod("tick", TickEvent.ServerTickEvent.class).invoke(null,
                new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, null));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
