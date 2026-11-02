package bin.cnpcplus.script.client;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.neoforged.fml.ModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/** Immutable signatures and provenance only; no API objects, initializers or script evaluation. */
public final class ScriptApiDocs {
    public static final String UNKNOWN = "?";
    private static final String API = "noppes.npcs.api.";
    private static List<Line> cached;
    private static Set<String> nativeHooks;
    private static final Map<String, String> hookSources = new HashMap<>();
    private static Map<String, List<String>> eventCandidates;
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

    /** Reads actual installed metadata; offline fixtures use this same path. */
    public static List<Line> index(List<Class<?>> classes, Function<String, String> sourceOf) {
        Map<String, Line> result = new TreeMap<>();
        Map<Class<?>, Set<String>> originals = new HashMap<>();
        Set<Class<?>> extraTypes = new LinkedHashSet<>();
        for (Class<?> owner : classes) {
            try {
                String source = sourceOf.apply(owner.getName());
                String group = "CNPC / " + owner.getPackageName().replace(API, "");
                Set<String> declaredOriginal = originals.computeIfAbsent(owner, ScriptApiDocs::originalMembers);
                for (Field field : owner.getDeclaredFields()) {
                    if (!Modifier.isPublic(field.getModifiers()) || field.isSynthetic()) continue;
                    boolean extra = declaredOriginal != null && !declaredOriginal.contains(field.getName() + ":" + org.objectweb.asm.Type.getDescriptor(field.getType()));
                    String origin = extra ? UNKNOWN : source;
                    put(result, new Line(extra ? "+ / " + origin : group, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, extra));
                    if (extra) extraTypes.add(field.getType());
                }
                for (Method method : owner.getDeclaredMethods()) {
                    if (!exposed(method)) continue;
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    boolean extra = merged != null || declaredOriginal != null && !declaredOriginal.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method));
                    String origin = extra ? merged == null ? UNKNOWN : sourceOf.apply(merged.mixin()) : source;
                    put(result, new Line(extra ? "+ / " + origin : group, owner.getName(), signature(method), origin, extra));
                    if (extra) extraTypes.add(method.getReturnType());
                }
                Class<?> runtime = ApiCompletion.runtimeWrapper(owner);
                if (runtime == null) continue;
                for (Method method : runtime.getMethods()) {
                    if (!exposed(method)) continue;
                    Set<String> original = originals.computeIfAbsent(method.getDeclaringClass(), ScriptApiDocs::originalMembers);
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    if (merged == null && original != null && original.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) continue;
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
                    // Merged fields have no MixinMerged provenance; the wrapper resource is not proof.
                    if (field.getDeclaringClass().getName().startsWith("noppes.")) origin = UNKNOWN;
                    put(result, new Line("+ / " + origin, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, true));
                    extraTypes.add(field.getType());
                }
            } catch (LinkageError | RuntimeException unavailable) { /* Broken optional metadata must not break the editor. */ }
        }
        Set<Class<?>> visited = new HashSet<>();
        while (!extraTypes.isEmpty()) {
            Class<?> owner = extraTypes.iterator().next(); extraTypes.remove(owner);
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
                for (Field field : owner.getFields()) if (!field.isSynthetic()) {
                    put(result, new Line("+ / " + origin, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, true));
                    extraTypes.add(field.getType());
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
        if (!context.equals("ForgeEvent")) {
            if (nativeHooks == null) {
                nativeHooks = new HashSet<>();
                try (InputStream stream = ScriptApiDocs.class.getResourceAsStream("/noppes/npcs/constants/EnumScriptType.class")) {
                    if (stream != null) new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if (!name.equals("<clinit>")) return null;
                            return new MethodVisitor(Opcodes.ASM9) {
                                private String lastString;
                                @Override public void visitLdcInsn(Object value) { if (value instanceof String text) lastString = text; }
                                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                                    if (owner.equals("noppes/npcs/constants/EnumScriptType") && name.equals("<init>")
                                            && descriptor.equals("(Ljava/lang/String;ILjava/lang/String;)V")) nativeHooks.add(lastString);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (IOException unavailable) { /* Cannot prove a native hook without the original enum. */ }
            }
            return nativeHooks.contains(hook) ? "CNPC" : UNKNOWN;
        }
        return hookSources.computeIfAbsent(hook, name -> {
            ModList mods = ModList.get(); if (mods == null) return UNKNOWN;
            if (eventCandidates == null) {
                eventCandidates = new HashMap<>();
                for (var file : mods.getModFiles()) for (var metadata : file.getFile().getScanResult().getClasses()) {
                    String className = metadata.clazz().getClassName(), simple = className.substring(className.lastIndexOf('.') + 1).replace("$", "");
                    if (!simple.isEmpty()) eventCandidates.computeIfAbsent(Character.toLowerCase(simple.charAt(0)) + simple.substring(1),
                            ignored -> new ArrayList<>()).add(className);
                }
            }
            Set<String> origins = new HashSet<>();
            for (String className : eventCandidates.getOrDefault(name, List.of())) {
                try {
                    Class<?> type = Class.forName(className, false, ScriptApiDocs.class.getClassLoader());
                    if (net.neoforged.bus.api.Event.class.isAssignableFrom(type)) origins.add(modSource(className));
                } catch (ClassNotFoundException | LinkageError unavailable) { /* Optional event metadata. */ }
            }
            return origins.size() == 1 ? origins.iterator().next() : UNKNOWN;
        });
    }
    private static void put(Map<String, Line> lines, Line line) { lines.putIfAbsent(line.group() + "\n" + line.owner() + "\n" + line.signature(), line); }
    private static boolean exposed(Method method) {
        return Modifier.isPublic(method.getModifiers()) && !method.isBridge() && !method.isSynthetic()
                && method.getDeclaringClass() != Object.class && !method.getName().contains("$");
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
        ModList mods = ModList.get(); if (mods == null) return UNKNOWN;
        String resource = name.replace('.', '/') + ".class", found = null;
        for (var info : mods.getModFiles()) {
            try {
                if (!java.nio.file.Files.isRegularFile(info.getFile().findResource(resource))) continue;
                String source = info.getMods().stream().map(mod -> mod.getDisplayName() + " [" + mod.getModId() + "]")
                        .collect(java.util.stream.Collectors.joining(" / "));
                if (source.isEmpty()) source = info.getFile().getFileName();
                if (found != null && !found.equals(source)) return UNKNOWN;
                found = source;
            } catch (RuntimeException unavailable) { /* Absent optional file. */ }
        }
        return found == null ? UNKNOWN : found;
    }
}
