package bin.cnpcplus.script.tools;

import java.lang.reflect.*;
import java.util.*;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/** One source for bindings, docs, completion and thread metadata. */
public final class ScriptApiRegistry {
    public enum ThreadPolicy { ANY_THREAD, MAIN_THREAD_ONLY, CLIENT_THREAD_ONLY, SNAPSHOT_ONLY }
    private static final class Module {
        final String name, lifecycle, notes; final Object tool; final ThreadPolicy policy;
        Module(String name, Object tool, ThreadPolicy policy, String lifecycle, String notes) {
            this.name = name; this.tool = tool; this.policy = policy; this.lifecycle = lifecycle; this.notes = notes;
        }
    }
    public static final class Parameter {
        private final String name, type;
        Parameter(String name, String type) { this.name = name; this.type = type; }
        public String name() { return name; } public String type() { return type; }
    }
    public static final class Entry {
        private final String publicName, module, returnType, side, lifecycle, behaviorNotes;
        private final ThreadPolicy threadPolicy; private final List<Parameter> parameters;
        Entry(String publicName, Module module, Method method, ThreadPolicy policy) {
            this.publicName = publicName; this.module = module.name; this.returnType = method.getReturnType().getSimpleName();
            this.threadPolicy = policy; this.side = policy == ThreadPolicy.MAIN_THREAD_ONLY ? "SERVER" : policy == ThreadPolicy.CLIENT_THREAD_ONLY ? "CLIENT" : "BOTH";
            this.lifecycle = module.lifecycle; this.behaviorNotes = module.notes;
            List<Parameter> params = new ArrayList<>();
            for (java.lang.reflect.Parameter p : method.getParameters()) params.add(new Parameter(p.getName(), p.getType().getSimpleName()));
            parameters = Collections.unmodifiableList(params);
        }
        public String publicName() { return publicName; } public String module() { return module; }
        public List<Parameter> parameters() { return parameters; } public String returnType() { return returnType; }
        public String side() { return side; } public ThreadPolicy threadPolicy() { return threadPolicy; }
        public String lifecycle() { return lifecycle; } public String since() { return "4.2.0"; }
        public String deprecatedSince() { return null; }
        public List<String> supportedVersions() { return Collections.singletonList("Forge 1.12.2"); }
        public String behaviorNotes() { return behaviorNotes; }
    }
    private static final List<Module> MODULES = Arrays.asList(
            new Module("Math", new ScriptMath(), ThreadPolicy.ANY_THREAD, "VALUE", "Degrees and blocks; inclusive geometry boundaries."),
            new Module("Text", new ScriptText(), ThreadPolicy.ANY_THREAD, "VALUE", "UTF-16, code points and Java 8 BreakIterator boundaries; newer emoji sequences may split."),
            new Module("UI", new ScriptUI(), ThreadPolicy.ANY_THREAD, "VALUE", "Pure layout calculations; Measure uses the client default font and GUI pixels."),
            new Module("WorldQuery", new ScriptWorld(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Loaded chunks only; floor coordinates; build height 0..255."),
            new Module("EntityQuery", new ScriptEntities(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Loaded entities only; distance ties use UUID."),
            new Module("RayTrace", new ScriptRayTrace(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Loaded chunks and neighbouring bounds only; COLLIDER/OUTLINE; VISUAL unsupported in 1.12.2."),
            new Module("Navigation", new ScriptNavigation(), ThreadPolicy.MAIN_THREAD_ONLY, "ENTITY", "Vanilla navigation; probes limited to one per 10 ticks; existing path reachability is UNKNOWN."),
            new Module("Teleport", new ScriptTeleport(), ThreadPolicy.MAIN_THREAD_ONLY, "WORLD", "Loaded chunks only; actual entity bounds; same-dimension transport; cross-dimension/vehicle transfer returns UNSUPPORTED."),
            new Module("Scheduler", new ScriptScheduler(), ThreadPolicy.MAIN_THREAD_ONLY, "SCRIPT", "Server ticks; script and owner lifetime; callbacks run on server thread."),
            new Module("Async", new ScriptAsync(), ThreadPolicy.MAIN_THREAD_ONLY, "SCRIPT", "Bounded SHA256/JSON_COMPACT workers; callbacks on server thread; duration in milliseconds."),
            new Module("Debug", new ScriptDebug(), ThreadPolicy.MAIN_THREAD_ONLY, "SERVER", "Bounded diagnostic snapshots; inspect hides string contents."),
            new Module("Cooldown", new ScriptCooldowns(), ThreadPolicy.MAIN_THREAD_ONLY, "SERVER", "Server ticks; per-owner IDs; runtime state."),
            new Module("events", new ScriptEvents(), ThreadPolicy.MAIN_THREAD_ONLY, "SCRIPT_PAGE", "NORMAL-priority synchronous Forge/CNPC events on the server thread; page-owned handles; save/delete/reload/unload cleans listeners; no worker/client callbacks."));
    private static final Map<String, Class<?>> ROOTS = new LinkedHashMap<>();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    static {
        for (Module module : MODULES) {
            ROOTS.put(module.name, module.tool.getClass()); methods(module, module.name, module.tool.getClass(), module.policy);
            for (Class<?> type : module.tool.getClass().getClasses()) {
                ThreadPolicy policy = module.policy;
                if (type == ScriptWorld.BlockSnapshot.class || type == ScriptWorld.PositionValidationOptions.class || type == ScriptEntities.EntitySnapshot.class
                        || type == ScriptTeleport.SearchOptions.class || type == ScriptTeleport.Options.class || type == ScriptTeleport.Result.class
                        || type == ScriptNavigation.PathSnapshot.class || type == ScriptNavigation.Options.class || type == ScriptAsync.Metrics.class) policy = ThreadPolicy.ANY_THREAD;
                methods(module, module.name + "." + type.getSimpleName(), type, policy);
            }
        }
        ROOTS.put("CNPCPlusMath", ScriptMath.class);
        methods(MODULES.get(2), "UI.Measure", ScriptMeasure.class, ThreadPolicy.CLIENT_THREAD_ONLY);
        methods(MODULES.get(1), "Text.Measure", ScriptMeasure.class, ThreadPolicy.CLIENT_THREAD_ONLY);
        methods(MODULES.get(0), "Math.Position", Position.class, ThreadPolicy.ANY_THREAD);
        methods(MODULES.get(0), "OperationResult", OperationResult.class, ThreadPolicy.ANY_THREAD);
        methods(MODULES.get(0), "Diagnostic", OperationResult.Diagnostic.class, ThreadPolicy.ANY_THREAD);
        ENTRIES.sort(Comparator.comparing(Entry::publicName));
    }
    private ScriptApiRegistry() { }
    private static void methods(Module module, String path, Class<?> type, ThreadPolicy policy) {
        for (Method method : type.getDeclaredMethods()) if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                && !method.isSynthetic() && !Arrays.asList("equals", "hashCode", "toString").contains(method.getName())) ENTRIES.add(new Entry(path + "." + method.getName(), module, method, policy));
    }
    public static Map<String, Class<?>> roots() { return Collections.unmodifiableMap(ROOTS); }
    public static List<Entry> entries() { return Collections.unmodifiableList(ENTRIES); }
    public static boolean isClientOnly(String publicName) { return ENTRIES.stream().anyMatch(e -> e.publicName.equals(publicName) && e.threadPolicy == ThreadPolicy.CLIENT_THREAD_ONLY); }
    public static void bind(Map<String, Object> bindings) { for (Module module : MODULES) bindings.put(module.name.equals("Math") ? "CNPCPlusMath" : module.name, module.tool); }
    public static void extendJavaScriptMath(ScriptEngine engine) throws ScriptException {
        Map<String, Integer> methods = new TreeMap<>();
        for (Entry entry : ENTRIES) if (entry.publicName.startsWith("Math.") && entry.publicName.indexOf('.', 5) < 0) methods.put(entry.publicName.substring(5), entry.parameters.size());
        StringBuilder code = new StringBuilder("(function(api){");
        for (Map.Entry<String, Integer> method : methods.entrySet()) {
            List<String> args = new ArrayList<>(); for (int i = 0; i < method.getValue(); i++) args.add("a" + i);
            String parameters = String.join(",", args);
            code.append("Object.defineProperty(Math,'").append(method.getKey()).append("',{value:function(").append(parameters)
                    .append("){return api.").append(method.getKey()).append('(').append(parameters).append(");},configurable:true});");
        }
        engine.eval(code.append("})(CNPCPlusMath)").toString());
    }
}
