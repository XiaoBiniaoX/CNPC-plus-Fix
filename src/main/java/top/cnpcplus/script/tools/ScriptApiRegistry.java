package top.cnpcplus.script.tools;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/** Shared registry for engine bindings, the in-game API browser, completion and lexical checks. */
public final class ScriptApiRegistry {
    public enum ThreadPolicy { ANY_THREAD, MAIN_THREAD_ONLY, CLIENT_THREAD_ONLY, SNAPSHOT_ONLY }
    private record Module(String name, Object tool, ThreadPolicy threadPolicy, String lifecycle, String notes) {}
    public record Parameter(String name, String type) {}
    public record Entry(String publicName, String module, List<Parameter> parameters, String returnType, String side,
                        ThreadPolicy threadPolicy, String lifecycle, String since, String deprecatedSince,
                        List<String> supportedVersions, String behaviorNotes) {}

    private static final List<Module> MODULES = List.of(
            new Module("Math", new ScriptMath(), ThreadPolicy.ANY_THREAD, "VALUE", "Degrees and blocks. AABB and sphere include the boundary."),
            new Module("Text", new ScriptText(), ThreadPolicy.ANY_THREAD, "VALUE", "Explicit UTF-16, code point and grapheme operations."),
            new Module("UI", new ScriptUI(), ThreadPolicy.ANY_THREAD, "VALUE", "Pure layout. Alignment names use uppercase."),
            new Module("WorldQuery", new ScriptWorld(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Loaded chunks only. Coordinates round down to blocks."),
            new Module("EntityQuery", new ScriptEntities(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Loaded entities only. Distance ties use UUID order."),
            new Module("RayTrace", new ScriptRayTrace(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Distance in blocks. Tracing stops at unloaded chunks."),
            new Module("Teleport", new ScriptTeleport(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Loaded chunks only. Cross-dimension travel requires ALLOW."),
            new Module("Navigation", new ScriptNavigation(), ThreadPolicy.MAIN_THREAD_ONLY, "ENTITY", "Native pathfinding. Probes are limited to once every 10 ticks per mob."),
            new Module("Scheduler", new ScriptScheduler(), ThreadPolicy.MAIN_THREAD_ONLY, "SCRIPT", "Server ticks. Script-scoped callbacks by default."),
            new Module("Async", new ScriptAsync(), ThreadPolicy.MAIN_THREAD_ONLY, "SCRIPT", "Wall-clock milliseconds. Detached jobs run in workers. Script callbacks run on the server."),
            new Module("Debug", new ScriptDebug(), ThreadPolicy.ANY_THREAD, "SERVER", "Bounded diagnostics. Capture and metrics require the server thread."),
            new Module("Cooldown", new ScriptCooldowns(), ThreadPolicy.MAIN_THREAD_ONLY, "SERVER", "Server ticks. Runtime state per owner and ID."));
    private static final Map<String, Class<?>> ROOTS = rootsMap();
    private static final List<Entry> ENTRIES = buildEntries();
    private static final Set<String> CLIENT_ONLY = ENTRIES.stream()
            .filter(entry -> entry.threadPolicy == ThreadPolicy.CLIENT_THREAD_ONLY)
            .map(Entry::publicName).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private ScriptApiRegistry() {}

    public static Map<String, Class<?>> roots() { return ROOTS; }
    public static List<Entry> entries() { return ENTRIES; }
    public static boolean isClientOnly(String publicName) { return CLIENT_ONLY.contains(publicName); }

    public static void bind(Map<String, Object> bindings) {
        for (Module module : MODULES) {
            bindings.put(module.name.equals("Math") ? "CNPCPlusMath" : module.name, module.tool);
        }
    }

    public static void extendJavaScriptMath(ScriptEngine engine) throws ScriptException {
        var functions = new java.util.TreeSet<String>();
        for (Entry entry : ENTRIES) {
            String name = entry.publicName;
            if (name.startsWith("Math.") && name.indexOf('.', 5) < 0) functions.add(name.substring(5));
        }
        StringBuilder code = new StringBuilder("(function(api){");
        for (String name : functions) {
            // Forward only the declared arguments. This also handles overloaded geometry methods in Nashorn.
            int count = ENTRIES.stream().filter(entry -> entry.publicName.equals("Math." + name)).findFirst().orElseThrow().parameters.size();
            String args = java.util.stream.IntStream.range(0, count).mapToObj(i -> "a" + i).collect(java.util.stream.Collectors.joining(","));
            code.append("Object.defineProperty(Math,'").append(name).append("',{ value:function(").append(args)
                    .append("){return api.").append(name).append('(').append(args).append(");}, configurable:true });");
        }
        engine.eval(code.append("})(CNPCPlusMath)").toString());
    }

    private static Map<String, Class<?>> rootsMap() {
        Map<String, Class<?>> roots = new LinkedHashMap<>();
        for (Module module : MODULES) roots.put(module.name, module.tool.getClass());
        roots.put("CNPCPlusMath", ScriptMath.class);
        return Map.copyOf(roots);
    }

    private static List<Entry> buildEntries() {
        var entries = new ArrayList<Entry>();
        for (Module module : MODULES) {
            methods(entries, module, module.name, module.tool.getClass());
            for (Class<?> type : module.tool.getClass().getClasses()) {
                if (type != ScriptUI.Scale.class && type != ScriptUI.Layout.class && !type.isRecord()
                        && !Set.of(ScriptEntities.Query.class, ScriptScheduler.TaskHandle.class, ScriptScheduler.TaskGroup.class,
                                ScriptAsync.Task.class, ScriptAsync.Group.class).contains(type)) continue;
                methods(entries, module, module.name + "." + type.getSimpleName(), type);
            }
        }
        Module math = MODULES.get(0);
        methods(entries, math, "Math.Position", Position.class);
        var shared = new Module("Shared", math.tool, ThreadPolicy.ANY_THREAD, "VALUE", "Immutable shared results.");
        methods(entries, shared, "OperationResult", OperationResult.class);
        methods(entries, shared, "Diagnostic", OperationResult.Diagnostic.class);
        entries.sort(Comparator.comparing(Entry::publicName).thenComparing(entry -> entry.parameters.toString()));
        return List.copyOf(entries);
    }

    private static void methods(List<Entry> entries, Module module, String path, Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || method.isSynthetic()
                    || Set.of("equals", "hashCode", "toString").contains(method.getName())) continue;
            List<Parameter> parameters = Arrays.stream(method.getParameters())
                    .map(p -> new Parameter(p.getName(), p.getType().getSimpleName())).toList();
            ThreadPolicy policy = module.threadPolicy;
            if (module.name.equals("Debug") && Set.of("getMetrics", "capture").contains(method.getName())) policy = ThreadPolicy.MAIN_THREAD_ONLY;
            else if (type == ScriptRayTrace.Result.class && method.getName().equals("entity")) policy = ThreadPolicy.MAIN_THREAD_ONLY;
            else if (type.isRecord() || type == Position.class || (type == ScriptAsync.Task.class && Set.of("getState", "getError", "isCancelled", "isDone").contains(method.getName()))) {
                policy = ThreadPolicy.ANY_THREAD;
            }
            String side = policy == ThreadPolicy.MAIN_THREAD_ONLY ? "SERVER" : policy == ThreadPolicy.CLIENT_THREAD_ONLY ? "CLIENT" : "BOTH";
            entries.add(new Entry(path + "." + method.getName(), module.name, parameters, method.getReturnType().getSimpleName(),
                    side, policy, module.lifecycle, "4.2.0", null, List.of("NeoForge 1.21.1"), module.notes));
        }
    }
}
