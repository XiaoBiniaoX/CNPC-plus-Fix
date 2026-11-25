package bin.cnpcplus.script.client;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.neoforged.fml.ModList;
import noppes.npcs.controllers.ScriptContainer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/** Reads registered globals and class metadata; never constructs API objects or evaluates scripts. */
public final class ScriptApiDocs {
    public static final String UNKNOWN = "?";
    private static final String API = "noppes.npcs.api.";
    private static List<Line> cached;
    private ScriptApiDocs() {}
    public record Line(String group, String owner, String signature, String source, boolean extension) {}

    public static List<Line> lines() {
        if (cached != null) return cached;
        Set<Class<?>> classes = new LinkedHashSet<>();
        try (InputStream stream = ScriptApiDocs.class.getResourceAsStream("/assets/cnpcplus/api-classes.txt")) {
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    for (String name : reader.lines().toList()) {
                        addClass(classes, name);
                    }
                }
            }
        } catch (IOException unavailable) { /* Empty index remains a usable browser. */ }
        discover(classes);
        Map<String, String> sources = new HashMap<>();
        cached = index(new ArrayList<>(classes), new HashMap<>(ScriptContainer.Data),
                name -> sources.computeIfAbsent(name, ScriptApiDocs::modSource));
        return cached;
    }

    /** Reuse NeoForge's scan: Mixin targets, implemented addon contracts and standalone API classes. */
    private static void discover(Set<Class<?>> classes) {
        ModList mods = ModList.get();
        if (mods == null) return;
        for (var info : mods.getModFiles()) {
            try {
                var scan = info.getFile().getScanResult();
                if (scan == null) continue;
                Set<String> mixins = new HashSet<>(), apiMixins = new HashSet<>();
                boolean addon = info.getMods().stream().anyMatch(mod -> mod.getDependencies().stream()
                        .anyMatch(dependency -> dependency.getModId().equals("customnpcs")));
                for (var annotation : scan.getAnnotations()) {
                    if (!annotation.annotationType().getClassName().equals("org.spongepowered.asm.mixin.Mixin")) continue;
                    String mixin = annotation.clazz().getClassName();
                    mixins.add(mixin);
                    for (String key : List.of("value", "targets")) {
                        if (!(annotation.annotationData().get(key) instanceof List<?> targets)) continue;
                        for (Object target : targets) {
                            String name = target instanceof org.objectweb.asm.Type type ? type.getClassName() : String.valueOf(target);
                            if (!name.startsWith(API) && !name.startsWith("noppes.npcs.entity.")) continue;
                            addon = true;
                            apiMixins.add(mixin);
                            addClass(classes, name);
                        }
                    }
                }
                for (var type : scan.getClasses()) {
                    String name = type.clazz().getClassName();
                    if (apiMixins.contains(name)) {
                        for (var contract : type.interfaces()) addClass(classes, contract.getClassName());
                    }
                    if (mixins.contains(name) || name.contains(".mixin.")) continue;
                    if (name.startsWith("noppes.") && type.interfaces().stream().anyMatch(contract -> contract.getClassName().startsWith(API)))
                        addClass(classes, name);
                    else if (addon && external(name) && (name.contains(".api.") || name.endsWith("API") || name.endsWith("Api")))
                        addClass(classes, name);
                }
            } catch (LinkageError | RuntimeException unavailable) { /* A broken addon cannot break the editor. */ }
        }
    }

    private static void addClass(Set<Class<?>> classes, String name) {
        if (name.contains(".mixin.") || name.contains(".mixins.")) return;
        try { classes.add(Class.forName(name, false, ScriptApiDocs.class.getClassLoader())); }
        catch (ClassNotFoundException | LinkageError | RuntimeException unavailable) { /* Optional signature. */ }
    }

    /** Reads actual installed metadata; offline fixtures use this same path. */
    public static List<Line> index(List<Class<?>> classes, Function<String, String> sourceOf) {
        return index(classes, Map.of(), sourceOf);
    }

    public static List<Line> index(List<Class<?>> classes, Map<String, ?> globals, Function<String, String> sourceOf) {
        Map<String, Line> result = new TreeMap<>();
        var registry = top.cnpcplus.script.tools.ScriptApiRegistry.entries();
        var roots = top.cnpcplus.script.tools.ScriptApiRegistry.roots();
        for (var entry : registry) {
            String parameters = entry.parameters().stream().map(parameter -> parameter.name() + ": " + parameter.type())
                    .collect(java.util.stream.Collectors.joining(", "));
            String signature = entry.publicName() + "(" + parameters + ") : " + entry.returnType() + " ["
                    + entry.threadPolicy() + ", " + entry.lifecycle() + "] " + entry.behaviorNotes();
            put(result, new Line("+ / CNPCplus / " + entry.module(), entry.module(), signature, "CNPCplus", true));
        }
        Map<Class<?>, Set<String>> originals = new HashMap<>();
        Set<Class<?>> extraTypes = new LinkedHashSet<>();
        for (var global : globals.entrySet()) {
            if (roots.containsKey(global.getKey())) continue;
            Object value = global.getValue();
            if (value == null) continue;
            Class<?> owner = value instanceof Class<?> type ? type : value.getClass();
            if (!external(owner.getName())) continue;
            String origin = sourceOf.apply(owner.getName());
            put(result, new Line("+ / " + origin, owner.getName(), global.getKey() + " : " + owner.getSimpleName(), origin, true));
            extraTypes.add(owner);
        }
        for (Class<?> owner : classes) {
            if (external(owner.getName())) { extraTypes.add(owner); continue; }
            try {
                String source = sourceOf.apply(owner.getName());
                String group = "CNPC / " + owner.getPackageName().replace(API, "");
                Set<String> declaredOriginal = originals.computeIfAbsent(owner, ScriptApiDocs::originalMembers);
                if (owner.getName().startsWith(API) && !owner.getName().startsWith(API + "wrapper.")) {
                    for (Field field : owner.getDeclaredFields()) {
                        if (!Modifier.isPublic(field.getModifiers()) || field.isSynthetic()) continue;
                        boolean extra = declaredOriginal != null && !declaredOriginal.contains(field.getName() + ":" + org.objectweb.asm.Type.getDescriptor(field.getType()));
                        String origin = extra ? UNKNOWN : source;
                        put(result, new Line(extra ? "+ / " + origin : group, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, extra));
                        if (extra) addTypes(extraTypes, field.getGenericType());
                    }
                    for (Method method : owner.getDeclaredMethods()) {
                        if (!exposed(method)) continue;
                        MixinMerged merged = method.getAnnotation(MixinMerged.class);
                        boolean extra = merged != null || declaredOriginal != null && !declaredOriginal.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method));
                        String origin = extra ? merged == null ? UNKNOWN : sourceOf.apply(merged.mixin()) : source;
                        put(result, new Line(extra ? "+ / " + origin : group, owner.getName(), signature(method), origin, extra));
                        if (extra) {
                            addTypes(extraTypes, method.getGenericReturnType());
                            for (Type parameter : method.getGenericParameterTypes()) addTypes(extraTypes, parameter);
                        }
                    }
                }
                Class<?> runtime = ApiCompletion.runtimeWrapper(owner);
                if (runtime == null) runtime = owner;
                for (Method method : runtime.getMethods()) {
                    if (!exposed(method)) continue;
                    Set<String> original = originals.computeIfAbsent(method.getDeclaringClass(), ScriptApiDocs::originalMembers);
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    if (merged == null && !external(method.getDeclaringClass().getName()) && original != null
                            && original.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) continue;
                    String contributor = merged == null ? method.getDeclaringClass().getName() : merged.mixin();
                    String origin = original == null && merged == null ? UNKNOWN : sourceOf.apply(contributor);
                    put(result, new Line("+ / " + origin, owner.getName(), signature(method), origin, true));
                    addTypes(extraTypes, method.getGenericReturnType());
                    for (Type parameter : method.getGenericParameterTypes()) addTypes(extraTypes, parameter);
                }
                for (Field field : runtime.getFields()) {
                    if (field.isSynthetic() || field.getName().contains("$")) continue;
                    Set<String> original = originals.computeIfAbsent(field.getDeclaringClass(), ScriptApiDocs::originalMembers);
                    if (original != null && original.contains(field.getName() + ":" + org.objectweb.asm.Type.getDescriptor(field.getType()))) continue;
                    String origin = original == null ? UNKNOWN : sourceOf.apply(field.getDeclaringClass().getName());
                    // Merged fields have no MixinMerged provenance; the wrapper resource is not proof.
                    if (field.getDeclaringClass().getName().startsWith("noppes.")) origin = UNKNOWN;
                    put(result, new Line("+ / " + origin, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, true));
                    addTypes(extraTypes, field.getGenericType());
                }
            } catch (LinkageError | RuntimeException unavailable) { /* Broken optional metadata must not break the editor. */ }
        }
        Set<Class<?>> visited = new HashSet<>();
        while (!extraTypes.isEmpty()) {
            Class<?> owner = extraTypes.iterator().next(); extraTypes.remove(owner);
            if (owner.isArray()) owner = owner.getComponentType();
            if (!visited.add(owner) || owner.isPrimitive() || !external(owner.getName())) continue;
            try {
                String origin = sourceOf.apply(owner.getName());
                if (UNKNOWN.equals(origin)) continue;
                for (Method method : owner.getMethods()) {
                    if (!exposed(method)) continue;
                    put(result, new Line("+ / " + origin, owner.getName(), signature(method), origin, true));
                    addTypes(extraTypes, method.getGenericReturnType());
                    for (Type parameter : method.getGenericParameterTypes()) addTypes(extraTypes, parameter);
                }
                for (Field field : owner.getFields()) if (!field.isSynthetic()) {
                    put(result, new Line("+ / " + origin, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, true));
                    addTypes(extraTypes, field.getGenericType());
                }
            } catch (LinkageError | RuntimeException unavailable) { /* Optional return-type metadata. */ }
        }
        return List.copyOf(result.values());
    }

    /** Only the current server-supplied Methods become hooks; unknown contributors stay unknown. */
    public static List<Line> hooks(String context, List<String> methods) {
        Map<String, Line> result = new TreeMap<>();
        for (String method : methods) {
            if (method == null || !method.matches("[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*")) continue;
            String source = hookSource(context, method);
            boolean extra = !source.equals("CNPC");
            put(result, new Line(extra ? "+ / " + source + " / hooks" : "CNPC / hooks", context,
                    method + "(event)", source, extra));
        }
        return List.copyOf(result.values());
    }

    public static String hookSource(String context, String hook) {
        return bin.cnpcplus.script.ScriptHookMetadata.hookSource(context, hook);
    }
    private static void put(Map<String, Line> lines, Line line) { lines.putIfAbsent(line.group() + "\n" + line.owner() + "\n" + line.signature(), line); }
    private static boolean exposed(Method method) {
        return Modifier.isPublic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic()
                && method.getDeclaringClass() != Object.class;
    }
    private static boolean external(String name) {
        return !name.startsWith("noppes.") && !name.startsWith("java.") && !name.startsWith("javax.")
                && !name.startsWith("net.minecraft.") && !name.startsWith("net.neoforged.")
                && !name.startsWith("net.minecraftforge.") && !name.startsWith("com.mojang.")
                && !name.startsWith("org.spongepowered.");
    }
    private static void addTypes(Set<Class<?>> types, Type type) {
        if (type instanceof Class<?> owner) types.add(owner);
        else if (type instanceof ParameterizedType generic) {
            addTypes(types, generic.getRawType());
            for (Type argument : generic.getActualTypeArguments()) addTypes(types, argument);
        } else if (type instanceof GenericArrayType array) addTypes(types, array.getGenericComponentType());
        else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) addTypes(types, bound);
            for (Type bound : wildcard.getLowerBounds()) addTypes(types, bound);
        }
    }
    private static String signature(Method method) {
        StringBuilder text = new StringBuilder(method.getName()).append('(');
        Type[] params = method.getGenericParameterTypes(); var names = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) text.append(", "); text.append(label(params[i]));
            if (names[i].isNamePresent()) text.append(' ').append(names[i].getName());
        }
        return text.append(") : ").append(label(method.getGenericReturnType())).toString();
    }
    private static String label(Type type) { return type.getTypeName().replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+", "").replace('$', '.'); }
    private static Set<String> originalMembers(Class<?> owner) {
        try (InputStream stream = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            if (stream == null) return null;
            Set<String> members = new HashSet<>();
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    members.add(name + descriptor); return null;
                }
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    members.add(name + ":" + descriptor); return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return members;
        } catch (IOException | RuntimeException unavailable) { return null; }
    }
    private static String modSource(String name) {
        return bin.cnpcplus.script.ScriptHookMetadata.modSource(name);
    }
}
