package top.cnpcplus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        public void say(String text) { messages.add(text); }
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
        checkTasks();
        ScriptEventsRegression.main(args);
        List<Path> scripts;
        try (var files = Files.walk(Path.of("游戏验收脚本"))) {
            scripts = files.filter(path -> path.toString().endsWith(".js")).sorted().toList();
        }
        check(scripts.size() >= 47, "categorized acceptance scripts missing");
        var examples = Map.of(
                "01-玩家距离与方向.js", List.of("距离=5.000", "单位向量=0.600,0.000,0.800", "前进2格目标=1.200,64.000,1.600"),
                "02-警戒区域判定.js", List.of("盒内=false 球内=false", "最近盒内点=3,64,3 到盒距离=1.000"),
                "03-任务进度与插值.js", List.of("ratio=0.35 百分比=35 进度条宽度=70", "平滑比例=0.28175"),
                "01-任务模板与颜色.js", List.of("[模板] &aAlex&r，任务：&e收集铁锭&r，进度 7/20", "[直接颜色码对照] §aAlex§r，任务：§e收集铁锭§r，进度 7/20", "颜色标记数=4", "[无颜色] Alex，任务：收集铁锭，进度 7/20", "[补零编号] 0007"),
                "02-中文表情安全截断.js", List.of("UTF16=6 码点=5 字素=4", "UTF16前3=小明 码点前3=小明😀"),
                "03-任务说明逐行输出.js", List.of("行数=3 每行上限=8", "[材料] 铁锭、煤炭、火把"),
                "01-居中面板与按钮坐标.js", List.of("面板 x=80 y=40 w=160 h=100", "按钮 x=120 y=112 w=80 h=20"),
                "02-双栏分割与缩放.js", List.of("[分栏 1] x=10 y=20 w=96 h=120", "[分栏 2] x=118 y=20 w=192 h=120", "屏幕160像素转GUI=80 GUI80转屏幕=160"));
        for (var path : scripts) {
            var script = Files.readString(path);
            ((Compilable) engine).compile(script);
            check(!script.contains(";") && ScriptStaticCheck.check(script, Set.of()) == null, "acceptance script lint failed: " + path);
            check(script.contains("e.npc.say(") && !script.contains(".broadcast(") && !script.contains("player.message("),
                    "acceptance output must use NPC say: " + path);
            if (Set.of("01-Math.js", "02-Text.js", "10-UI.js").contains(path.getFileName().toString())) {
                Chat chat = new Chat();
                engine.put("acceptanceChat", chat);
                engine.eval(script + "\ninteract({npc: acceptanceChat})");
                check(chat.messages.size() == 1 && chat.messages.get(0).contains("=true") && !chat.messages.get(0).contains("=false"),
                        "unexpected acceptance output for " + path + ": " + chat.messages);
                System.out.println(chat.messages.get(0));
            }
            var expected = examples.get(path.getFileName().toString());
            if (expected != null) {
                Chat chat = new Chat();
                engine.put("acceptanceChat", chat);
                // Only positions/player name are fixtures; Math/Text/UI are the real registered Java tools.
                engine.eval("(function(){var WorldQuery={getPosition:function(entity){return entity===acceptanceChat"
                        + "?Math.position(0,64,0):Math.position(3,64,4)}};\n" + script
                        + "\ninteract({npc:acceptanceChat,player:{getName:function(){return 'Alex'}}})})()");
                String output = String.join("\n", chat.messages);
                for (String fragment : expected) check(output.contains(fragment), "example output mismatch: " + path + "\n" + output);
                check(!output.contains("undefined"), "undefined example data: " + path);
                System.out.println("VALUE_EXAMPLE=PASS " + path.getFileName());
            }
        }
        System.out.println("ACCEPTANCE_SCRIPTS=" + scripts.size());
        String source = "function tickfix(event) { event.wo";
        check(ScriptApiCompletion.complete(source, source.length(), "ItemEvent").entries.stream()
                .anyMatch(entry -> entry.name.equals("world")), "carrier world completion missing");
        check(ScriptApiDocs.index(List.of(), name -> "CNPCplus").stream().anyMatch(line ->
                line.signature().contains("Cooldown.start(") && line.signature().contains("durationTicks: int") &&
                         line.signature().contains("MAIN_THREAD_ONLY")), "registry documentation missing");
        for (String method : List.of("on", "off", "offAll", "has", "list")) {
            String completion = "events.";
            check(ScriptApiCompletion.complete(completion, completion.length(), "NpcEvent").entries.stream()
                    .anyMatch(entry -> entry.name.equals(method)), "events completion missing: " + method);
            check(ScriptApiRegistry.entries().stream().anyMatch(entry -> entry.publicName().equals("events." + method)),
                    "events registry documentation missing: " + method);
        }
        try {
            engine.eval("WorldQuery.isChunkLoaded(null,0,0)");
            throw new AssertionError("world call accepted without server thread");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("CNPCPLUS.THREAD.MAIN_THREAD_ONLY"), "wrong thread failure");
        }
        System.out.println("UNIFIED_SCRIPT_REGISTRY_NASHORN_AND_SELECTORS=PASS");
    }

    private static void checkTasks() throws Exception {
        // Replace only the live-server boundary; execute the production scheduler and scope callbacks.
        var loader = new ClassLoader(UnifiedScriptRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("top.cnpcplus.script.tools.ScriptScheduler")
                        && !name.startsWith("top.cnpcplus.script.tools.ScriptAsync")
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
                + "var player = {getWorld:function(){return null}};var acceptanceEvent={npc:chat,player:player};");
        String script = Files.readString(Path.of("游戏验收脚本/00-综合回归/08-Scheduler.js"));
        engine.eval(script.replace("Scheduler.createGroup(player)", "Scheduler.createGlobalGroup()")
                + "\ninteract(acceptanceEvent)");
        var tick = type.getMethod("tick", TickEvent.ServerTickEvent.class);
        var event = new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, null);
        for (int i = 0; i < 65; i++) tick.invoke(null, event);
        check(chat.messages.size() == 2 && chat.messages.get(1).equals(
                "[08 Scheduler] once=true repeating=3 withDelay=3 cancel=true pauseResume=true"),
                 "scheduler acceptance failed: " + chat.messages);
        System.out.println(chat.messages.get(1));
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
        System.out.println("SCHEDULER_OWNER_CANCELLATION=PASS");
        for (String name : List.of("01-五秒倒计时.js", "02-暂停后恢复任务.js", "03-再次右键取消任务组.js")) {
            chat.messages.clear();
            script = Files.readString(Path.of("游戏验收脚本/08-定时调度", name));
            // Fake NPC cannot supply a live entity owner; keep the real script-scoped group instead.
            engine.eval(script.replace("Scheduler.createGroup(e.npc)", "Scheduler.createGroup()") + "\ninteract(acceptanceEvent)");
            if (!name.startsWith("02-")) engine.eval("interact(acceptanceEvent)");
            for (int i = 0; i < 110; i++) tick.invoke(null, event);
            String output = String.join("\n", chat.messages);
            if (name.startsWith("01-")) {
                check(chat.messages.size() == 8 && output.contains("状态=CANCELLED") && output.contains("剩余=0秒"), output);
            } else if (name.startsWith("02-")) {
                check(chat.messages.size() == 4 && output.contains("无计划刻=true") && output.contains("最终状态=COMPLETED"), output);
            } else {
                check(chat.messages.size() == 3 && output.contains("下一刻状态=CANCELLED") && !output.contains("第一次提醒"), output);
            }
            System.out.println("SCHEDULER_EXAMPLE=PASS " + name);
        }
        var async = Class.forName("top.cnpcplus.script.tools.ScriptAsync", true, loader);
        var task = Class.forName("top.cnpcplus.script.tools.ScriptAsync$Task", true, loader);
        var future = task.getDeclaredField("future");
        future.setAccessible(true);
        var asyncTick = async.getMethod("tick", TickEvent.ServerTickEvent.class);
        engine.put("Async", async.getConstructor().newInstance());
        for (String name : List.of("01-并行摘要与JSON压缩.js", "02-超时后回退.js", "03-批量取消异步任务.js")) {
            chat.messages.clear();
            script = Files.readString(Path.of("游戏验收脚本/09-异步任务", name));
            // Expose local handles only to await worker completion deterministically, without sleeps/polling.
            engine.eval("var exampleHash,exampleJson,exampleTimed;\n"
                    + script.replace("var hash =", "var hash = exampleHash =")
                            .replace("var json =", "var json = exampleJson =")
                            .replace("var timed =", "var timed = exampleTimed =")
                    + "\ninteract(acceptanceEvent)");
            if (name.startsWith("01-")) {
                for (String key : List.of("exampleHash", "exampleJson")) {
                    ((java.util.concurrent.CompletableFuture<?>) future.get(engine.get(key))).get(5, java.util.concurrent.TimeUnit.SECONDS);
                }
            } else if (name.startsWith("02-")) {
                var pending = (java.util.concurrent.CompletableFuture<?>) future.get(engine.get("exampleTimed"));
                check(pending.handle((value, error) -> error).get(5, java.util.concurrent.TimeUnit.SECONDS) != null, "timeout did not fail");
            }
            for (int i = 0; i < 10; i++) asyncTick.invoke(null, event);
            String output = String.join("\n", chat.messages);
            if (name.startsWith("01-")) {
                check(chat.messages.size() == 3 && output.contains("SHA256=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                        && output.contains("JSON={\"quest\":\"iron\",\"count\":7}"), output);
            } else if (name.startsWith("02-")) {
                check(chat.messages.size() == 3 && output.contains("CNPCPLUS.ASYNC.TIMED_OUT")
                        && output.contains("最终文本=默认任务说明 包装状态=FAILED 源状态=CANCELLED"), output);
            } else {
                check(chat.messages.size() == 2 && output.contains("CANCELLED cancelled=true done=true"), output);
            }
            System.out.println("ASYNC_EXAMPLE=PASS " + name);
        }
        async.getMethod("stop", net.minecraftforge.event.server.ServerStoppedEvent.class).invoke(null, new Object[] {null});
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
