package top.cnpcplus.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/** Immutable signatures only. No API objects, static initializers, script evaluation or descriptions. */
public final class ScriptApiDocs {
    public static final String UNKNOWN = "?";
    private static final String API = "noppes.npcs.api.";
    private static List<Line> cached;
    private ScriptApiDocs() {}

    public record Line(String group, String owner, String signature, String source, boolean extension) {}

    public static List<Line> lines() {
        if (cached != null) return cached;
        List<Class<?>> classes = new ArrayList<>();
        try (InputStream stream = ScriptApiDocs.class.getResourceAsStream("/assets/cnpcplus/api-classes.txt")) {
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    for (String name : reader.lines().toList()) {
                        try { classes.add(Class.forName(name, false, ScriptApiDocs.class.getClassLoader())); }
                        catch (ClassNotFoundException | LinkageError | SecurityException unavailable) { /* Optional signature. */ }
                    }
                }
            }
        } catch (IOException unavailable) { /* Empty index remains a usable browser. */ }
        Map<String, String> sources = new HashMap<>();
        cached = index(classes, name -> sources.computeIfAbsent(name, ScriptApiDocs::modSource));
        return cached;
    }

    /** Also used by the offline Mixin-shaped fixture; reads metadata, never invokes members. */
    public static List<Line> index(List<Class<?>> classes, Function<String, String> sourceOf) {
        Map<String, Line> result = new TreeMap<>();
        Map<Class<?>, Set<String>> originals = new HashMap<>();
        Set<Class<?>> extraTypes = new LinkedHashSet<>();
        for (Class<?> owner : classes) {
            try {
                String source = sourceOf.apply(owner.getName());
                String group = "CNPC / " + owner.getPackageName().replace(API, "");
                for (Field field : owner.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isPublic(field.getModifiers()) || field.isSynthetic()) continue;
                    put(result, new Line(group, owner.getName(), field.getName() + " : " + label(field.getGenericType()), source, false));
                }
                for (Method method : owner.getDeclaredMethods()) {
                    if (exposed(method)) put(result, new Line(group, owner.getName(), signature(method), source, false));
                }
                Class<?> runtime = ScriptApiCompletion.runtimeWrapper(owner);
                if (runtime == null) continue;
                for (Method method : runtime.getMethods()) {
                    if (!exposed(method)) continue;
                    Set<String> original = originals.computeIfAbsent(method.getDeclaringClass(), ScriptApiDocs::originalMembers);
                    if (original != null && original.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) continue;
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    String contributor = merged == null ? method.getDeclaringClass().getName() : merged.mixin();
                    String origin = original == null && merged == null ? UNKNOWN : sourceOf.apply(contributor);
                    put(result, new Line("+ / " + origin, owner.getName(), signature(method), origin, true));
                    extraTypes.add(method.getReturnType());
                }
                for (Field field : runtime.getFields()) {
                    if (field.isSynthetic() || field.getName().contains("$")) continue;
                    Set<String> original = originals.computeIfAbsent(field.getDeclaringClass(), ScriptApiDocs::originalMembers);
                    if (original != null && original.contains(field.getName() + ":" + org.objectweb.asm.Type.getDescriptor(field.getType()))) continue;
                    String origin = original == null ? UNKNOWN : sourceOf.apply(field.getDeclaringClass().getName());
                    // Merged fields have no MixinMerged provenance: the wrapper file does not prove the contributor.
                    if (field.getDeclaringClass().getName().startsWith("noppes.")) origin = UNKNOWN;
                    put(result, new Line("+ / " + origin, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, true));
                    extraTypes.add(field.getType());
                }
            } catch (LinkageError | RuntimeException unavailable) { /* A broken optional API cannot break the editor. */ }
        }
        // Follow addon-owned objects actually returned by the exposed APIs, not every mod's internal classes.
        Set<Class<?>> visited = new HashSet<>();
        while (!extraTypes.isEmpty()) {
            Class<?> owner = extraTypes.iterator().next();
            extraTypes.remove(owner);
            if (owner.isArray()) owner = owner.getComponentType();
            if (!visited.add(owner) || owner.isPrimitive() || owner.getName().startsWith("java.")
                    || owner.getName().startsWith("net.minecraft.") || owner.getName().startsWith("noppes.")) continue;
            try {
                String origin = sourceOf.apply(owner.getName());
                if (UNKNOWN.equals(origin)) continue;
                for (Method method : owner.getMethods()) {
                    if (!exposed(method)) continue;
                    put(result, new Line("+ / " + origin, owner.getName(), signature(method), origin, true));
                    extraTypes.add(method.getReturnType());
                }
                for (Field field : owner.getFields()) {
                    if (!field.isSynthetic()) {
                        put(result, new Line("+ / " + origin, owner.getName(),
                                field.getName() + " : " + label(field.getGenericType()), origin, true));
                        extraTypes.add(field.getType());
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
                && method.getDeclaringClass() != Object.class && !method.getName().contains("$");
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
