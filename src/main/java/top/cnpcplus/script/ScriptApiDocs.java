package top.cnpcplus.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraftforge.fml.ModList;
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

    /** Forge already scanned these classes: do not load Mixin classes or scan unrelated mod internals. */
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
                    // DataAI/DataDisplay and other native implementations have no *Wrapper naming convention.
                    if (name.startsWith("noppes.") && type.interfaces().stream().anyMatch(contract -> contract.getClassName().startsWith(API)))
                        addClass(classes, name);
                    else if (addon && external(name) && (name.contains(".api.") || name.endsWith("API") || name.endsWith("Api")))
                        addClass(classes, name);
                }
            } catch (LinkageError | RuntimeException unavailable) { /* An optional addon cannot break the editor. */ }
        }
    }

    private static void addClass(Set<Class<?>> classes, String name) {
        if (name.contains(".mixin.") || name.contains(".mixins.")) return;
        try { classes.add(Class.forName(name, false, ScriptApiDocs.class.getClassLoader())); }
        catch (ClassNotFoundException | LinkageError | RuntimeException unavailable) { /* Optional signature. */ }
    }

    /** Also used by the offline Mixin-shaped fixture; reads metadata, never invokes members. */
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
                if (owner.getName().startsWith(API) && !owner.getName().startsWith(API + "wrapper.")) {
                    for (Field field : owner.getDeclaredFields()) {
                        if (!java.lang.reflect.Modifier.isPublic(field.getModifiers()) || field.isSynthetic()) continue;
                        put(result, new Line(group, owner.getName(), field.getName() + " : " + label(field.getGenericType()), source, false));
                    }
                    for (Method method : owner.getDeclaredMethods()) {
                        if (exposed(method)) put(result, new Line(group, owner.getName(), signature(method), source, false));
                    }
                }
                Class<?> runtime = ScriptApiCompletion.runtimeWrapper(owner);
                if (runtime == null) runtime = owner;
                for (Method method : runtime.getMethods()) {
                    if (!exposed(method)) continue;
                    Set<String> original = originals.computeIfAbsent(method.getDeclaringClass(), ScriptApiDocs::originalMembers);
                    if (!external(method.getDeclaringClass().getName()) && original != null
                            && original.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) continue;
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
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
                    // Merged fields have no MixinMerged provenance: the wrapper file does not prove the contributor.
                    if (field.getDeclaringClass().getName().startsWith("noppes.")) origin = UNKNOWN;
                    put(result, new Line("+ / " + origin, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, true));
                    addTypes(extraTypes, field.getGenericType());
                }
            } catch (LinkageError | RuntimeException unavailable) { /* A broken optional API cannot break the editor. */ }
        }
        // Follow registered API roots and their signature types, including List<AddonApi> and enums.
        Set<Class<?>> visited = new HashSet<>();
        while (!extraTypes.isEmpty()) {
            Class<?> owner = extraTypes.iterator().next();
            extraTypes.remove(owner);
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
                for (Field field : owner.getFields()) {
                    if (!field.isSynthetic()) {
                        put(result, new Line("+ / " + origin, owner.getName(),
                                field.getName() + " : " + label(field.getGenericType()), origin, true));
                        addTypes(extraTypes, field.getGenericType());
                    }
                }
            } catch (LinkageError | RuntimeException unavailable) { /* Optional return-type metadata. */ }
        }
        return List.copyOf(result.values());
    }

    private static void put(Map<String, Line> lines, Line line) {
        lines.putIfAbsent(line.group + "\n" + line.owner + "\n" + line.signature, line);
    }

    private static boolean exposed(Method method) {
        return java.lang.reflect.Modifier.isPublic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic()
                && method.getDeclaringClass() != Object.class;
    }

    private static boolean external(String name) {
        return !name.startsWith("noppes.") && !name.startsWith("java.") && !name.startsWith("javax.")
                && !name.startsWith("net.minecraft.") && !name.startsWith("net.minecraftforge.")
                && !name.startsWith("com.mojang.") && !name.startsWith("org.spongepowered.");
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
        Type[] params = method.getGenericParameterTypes();
        var names = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) text.append(", ");
            text.append(label(params[i]));
            if (names[i].isNamePresent()) text.append(' ').append(names[i].getName());
        }
        return text.append(") : ").append(label(method.getGenericReturnType())).toString();
    }

    private static String label(Type type) {
        return type.getTypeName().replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+", "").replace('$', '.');
    }

    private static Set<String> originalMembers(Class<?> owner) {
        String resource = "/" + owner.getName().replace('.', '/') + ".class";
        try (InputStream stream = owner.getResourceAsStream(resource)) {
            if (stream == null) return null;
            Set<String> members = new HashSet<>();
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    members.add(name + descriptor);
                    return null;
                }
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    members.add(name + ":" + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return members;
        } catch (IOException | RuntimeException unavailable) { return null; }
    }

    private static String modSource(String name) {
        ModList mods = ModList.get();
        if (mods == null) return UNKNOWN;
        String resource = name.replace('.', '/') + ".class";
        String found = null;
        for (var info : mods.getModFiles()) {
            try {
                if (!java.nio.file.Files.isRegularFile(info.getFile().findResource(resource))) continue;
                String source = info.getMods().stream().map(mod -> mod.getDisplayName() + " [" + mod.getModId() + "]")
                        .collect(java.util.stream.Collectors.joining(" / "));
                if (source.isEmpty()) source = info.getFile().getFileName();
                if (found != null && !found.equals(source)) return UNKNOWN; // Ambiguous duplicate resource.
                found = source;
            } catch (RuntimeException unavailable) { /* Absent optional file. */ }
        }
        return found == null ? UNKNOWN : found;
    }
}
