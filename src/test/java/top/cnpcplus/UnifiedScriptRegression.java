package top.cnpcplus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.script.Compilable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineManager;
import net.minecraftforge.event.TickEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import top.cnpcplus.compat.LegacyJeiMixinGuard;
import top.cnpcplus.script.ScriptApiCompletion;
import top.cnpcplus.script.ScriptApiDocs;
import top.cnpcplus.script.ScriptStaticCheck;
import top.cnpcplus.script.tools.ScriptApiRegistry;

public final class UnifiedScriptRegression {
    public static final class Chat {
        private final List<String> messages = new ArrayList<>();
        public void message(String text) { messages.add(text); }
    }

    public static void main(String[] args) throws Exception {
        checkSelectors();
        var engine = new ScriptEngineManager().getEngineByName("nashorn");
        check(engine != null, "Nashorn required");
        ScriptApiRegistry.bind(engine.getBindings(ScriptContext.ENGINE_SCOPE));
        ScriptApiRegistry.extendJavaScriptMath(engine);
        Object result = engine.eval("""
                Math.floor(2.9) === 2 && Math.wrapDegrees(180) === -180 &&
                Math.position(0, 0, 0).normalize().isZero() &&
                Math.containsPoint(Math.box(Math.position(0,0,0),Math.position(1,1,1)),Math.position(1,1,1)) &&
                Math.containsPoint(Math.sphere(Math.position(0,0,0),1),Math.position(1,0,0)) &&
                Text.truncate('A😀',2) === 'A' && Text.lengthGraphemes('a\\u0301') === 1 &&
                UI.Layout.center(UI.rect(0,0,100,50),UI.size(20,10)).x() === 40
                """);
        check(Boolean.TRUE.equals(result), "Nashorn value or method binding failed");
        engine.put("taskClock", new java.util.concurrent.atomic.AtomicLong(-1));
        check(Boolean.TRUE.equals(engine.eval("taskClock.get() !== -1 && Number(taskClock.get()) === -1")),
                "Nashorn long sentinel behavior changed");
        checkScheduler();
        List<Path> scripts;
        try (var files = Files.list(Path.of("游戏验收脚本"))) {
            scripts = files.filter(path -> path.toString().endsWith(".js")).sorted().toList();
        }
        check(scripts.size() >= 14, "numbered acceptance scripts missing");
        for (var path : scripts) {
            var script = Files.readString(path);
            ((Compilable) engine).compile(script);
            check(!script.contains(";") && ScriptStaticCheck.check(script, Set.of()) == null, "acceptance script lint failed: " + path);
            if (Set.of("01-Math.js", "02-Text.js", "10-UI.js").contains(path.getFileName().toString())) {
                Chat chat = new Chat();
                engine.put("acceptanceChat", chat);
                engine.eval(script + "\ninteract({player: acceptanceChat})");
                check(chat.messages.size() == 1 && chat.messages.get(0).contains("=true") && !chat.messages.get(0).contains("=false"),
                        "unexpected acceptance output for " + path + ": " + chat.messages);
                System.out.println(chat.messages.get(0));
            }
        }
        System.out.println("ACCEPTANCE_SCRIPTS=" + scripts.size());
        String source = "function tickfix(event) { event.wo";
        check(ScriptApiCompletion.complete(source, source.length(), "ItemEvent").entries.stream()
                .anyMatch(entry -> entry.name.equals("world")), "carrier world completion missing");
        check(ScriptApiDocs.index(List.of(), name -> "CNPCplus").stream().anyMatch(line ->
                line.signature().contains("Cooldown.start(") && line.signature().contains("durationTicks: int") &&
                        line.signature().contains("MAIN_THREAD_ONLY")), "registry documentation missing");
        try {
            engine.eval("WorldQuery.isChunkLoaded(null,0,0)");
            throw new AssertionError("world call accepted without server thread");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("CNPCPLUS.THREAD.MAIN_THREAD_ONLY"), "wrong thread failure");
        }
        System.out.println("UNIFIED_SCRIPT_REGISTRY_NASHORN_AND_SELECTORS=PASS");
    }

    private static void checkScheduler() throws Exception {
        // Replace only the live-server boundary; execute the production scheduler and scope callbacks.
        var loader = new ClassLoader(UnifiedScriptRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("top.cnpcplus.script.tools.ScriptScheduler")
                        && !name.startsWith("top.cnpcplus.script.tools.ScriptScope")
                        && !name.startsWith("top.cnpcplus.script.tools.ScriptWorld")) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                try (var in = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    if (name.equals("top.cnpcplus.script.tools.ScriptWorld")) {
                        var node = new ClassNode();
                        new ClassReader(bytes).accept(node, 0);
                        var server = node.methods.stream().filter(method -> method.name.equals("server")).findFirst().orElseThrow();
                        server.instructions.clear();
                        server.tryCatchBlocks.clear();
                        if (server.localVariables != null) server.localVariables.clear();
                        server.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                        server.instructions.add(new InsnNode(Opcodes.ARETURN));
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
        var type = Class.forName("top.cnpcplus.script.tools.ScriptScheduler", true, loader);
        var engine = new ScriptEngineManager(loader).getEngineByName("nashorn");
        var chat = new Chat();
        engine.put("Scheduler", type.getConstructor().newInstance());
        engine.put("chat", chat);
        engine.eval("var WorldQuery = {getPosition:function(p){return null},isPositionLoaded:function(w,p){return true}};"
                + "var player = {getWorld:function(){return null},message:function(s){chat.message(s)}};");
        String script = Files.readString(Path.of("游戏验收脚本/08-Scheduler.js"));
        engine.eval(script.replace("Scheduler.createGroup(player)", "Scheduler.createGlobalGroup()")
                + "\ninteract({player:player})");
        var tick = type.getMethod("tick", TickEvent.ServerTickEvent.class);
        var event = new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, null);
        for (int i = 0; i < 65; i++) tick.invoke(null, event);
        check(chat.messages.size() == 2 && chat.messages.get(1).equals(
                "[08 Scheduler] once=true repeating=3 withDelay=3 cancel=true pauseResume=true"),
                "scheduler acceptance failed: " + chat.messages);
        var scope = Class.forName("top.cnpcplus.script.tools.ScriptScope", true, loader);
        var inherited = scope.getDeclaredField("INHERITED");
        inherited.setAccessible(true);
        @SuppressWarnings("unchecked") var lifetime = (ThreadLocal<java.util.function.BooleanSupplier>) inherited.get(null);
        var alive = new java.util.concurrent.atomic.AtomicBoolean(true);
        lifetime.set(alive::get);
        engine.eval("var expired = Scheduler.runLater(5,function(){throw new Error('expired callback ran')})");
        lifetime.remove();
        alive.set(false);
        tick.invoke(null, event);
        check(Boolean.TRUE.equals(engine.eval("expired.isCancelled()")), "expired script task was not cancelled");
        System.out.println(chat.messages.get(1));
        System.out.println("SCHEDULER_OWNER_CANCELLATION=PASS");
    }

    private static void checkSelectors() throws Exception {
        String enumName = "noppes.npcs.constants.EnumScriptType";
        var loader = new ClassLoader(UnifiedScriptRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(enumName) && !name.startsWith("top.cnpcplus.script.ExtraScriptHook")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type != null) return type;
                try (var in = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    if (name.equals(enumName)) {
                        var node = new ClassNode();
                        new ClassReader(bytes).accept(node, 0);
                        new LegacyJeiMixinGuard().postApply(name, node, "top.cnpcplus.mixin.MixinScriptTypes", null);
                        var writer = new ClassWriter(0);
                        node.accept(writer);
                        bytes = writer.toByteArray();
                    }
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException error) {
                    throw new ClassNotFoundException(name, error);
                }
            }
        };
        var types = Class.forName(enumName, true, loader);
        Object tickfix = types.getMethod("valueOf", String.class).invoke(null, "TICKFIX");
        Object tick = types.getField("TICK").get(null);
        Object timer = types.getField("TIMER").get(null);
        for (String target : List.of("npc", "player", "block", "door", "item")) {
            var methods = List.of((Object[]) types.getField(target + "Scripts").get(null));
            check(methods.contains(tickfix) && methods.contains(tick), "missing tick selector on " + target);
            if (target.equals("item")) check(methods.contains(timer), "missing carrier timer selector");
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
