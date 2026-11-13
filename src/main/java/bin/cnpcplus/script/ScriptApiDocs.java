package bin.cnpcplus.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import noppes.npcs.controllers.ScriptContainer;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/** Reads registered globals and class metadata; never constructs API objects or evaluates scripts. */
public final class ScriptApiDocs {
    public static final String UNKNOWN = "?";
    private static final String API = "noppes.npcs.api.";
    private static List<Line> cached;
    private static final Set<String> installedTypes = new LinkedHashSet<>();
    private ScriptApiDocs() { }
    public static final class Line {
        public final String group, owner, signature, source;
        public final boolean extension;
        public Line(String group, String owner, String signature, String source, boolean extension) {
            this.group = group; this.owner = owner; this.signature = signature; this.source = source; this.extension = extension;
        }
    }
    public static List<Line> lines() {
        if (cached != null) return cached;
        Set<Class<?>> classes = new LinkedHashSet<>();
        try (InputStream stream = ScriptApiDocs.class.getResourceAsStream("/assets/cnpcplus/api-classes.txt")) {
            if (stream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (String name; (name = reader.readLine()) != null;) {
                    addClass(classes, name);
                }
            }
        } catch (IOException unavailable) { /* Missing index remains an empty browser. */ }
        for (String name : installedTypes) addClass(classes, name);
        Map<String, String> sources = new HashMap<>();
        cached = index(new ArrayList<>(classes), globals(), name -> sources.computeIfAbsent(name, ScriptApiDocs::modSource));
        return cached;
    }

    /** Capture names from Forge's existing scan at preInit; do not retain the whole table or load Mixins. */
    public static void discover(ASMDataTable data) {
        if (data == null) return;
        Set<ModCandidate> candidates = new HashSet<>();
        Set<File> addonFiles = new HashSet<>();
        Set<String> mixins = new HashSet<>();
        for (ModContainer mod : Loader.instance().getModList()) {
            if (mod.getDependencies().stream().anyMatch(dependency -> dependency.getLabel().equals("customnpcs")))
                addonFiles.add(mod.getSource());
        }
        for (ASMDataTable.ASMData annotation : data.getAll("net.minecraftforge.fml.common.Mod"))
            candidates.add(annotation.getCandidate());
        for (ASMDataTable.ASMData annotation : data.getAll("org.spongepowered.asm.mixin.Mixin")) {
            candidates.add(annotation.getCandidate());
            String mixin = annotation.getClassName().replace('/', '.');
            mixins.add(mixin);
            boolean apiMixin = false;
            for (String key : Arrays.asList("value", "targets")) {
                Object targets = annotation.getAnnotationInfo().get(key);
                if (!(targets instanceof Collection<?>)) continue;
                for (Object target : (Collection<?>) targets) {
                    String name = target instanceof org.objectweb.asm.Type ? ((org.objectweb.asm.Type) target).getClassName() : String.valueOf(target);
                    if (!name.startsWith(API) && !name.startsWith("noppes.npcs.entity.")) continue;
                    apiMixin = true;
                    installedTypes.add(name);
                    addonFiles.add(annotation.getCandidate().getModContainer());
                }
            }
            if (apiMixin) {
                try (InputStream stream = ScriptApiDocs.class.getResourceAsStream("/" + mixin.replace('.', '/') + ".class")) {
                    if (stream != null) for (String contract : new ClassReader(stream).getInterfaces())
                        installedTypes.add(contract.replace('/', '.'));
                } catch (IOException | RuntimeException unavailable) { /* Optional Mixin metadata. */ }
            }
        }
        for (ModCandidate candidate : candidates) {
            if (candidate == null || !addonFiles.contains(candidate.getModContainer())) continue;
            for (String entry : candidate.getClassList()) {
                String name = entry.replace('/', '.');
                if (!mixins.contains(name) && external(name) && (name.contains(".api.") || name.endsWith("API") || name.endsWith("Api")))
                    installedTypes.add(name);
            }
        }
        cached = null;
    }

    private static void addClass(Set<Class<?>> classes, String name) {
        if (name.contains(".mixin.") || name.contains(".mixins.")) return;
        try { classes.add(Class.forName(name, false, ScriptApiDocs.class.getClassLoader())); }
        catch (ClassNotFoundException | LinkageError | RuntimeException unavailable) { /* Optional signature. */ }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> globals() {
        try {
            Field field = ScriptContainer.class.getDeclaredField("Data");
            field.setAccessible(true);
            return new HashMap<>((Map<String, ?>) field.get(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return Collections.emptyMap();
        }
    }

    public static List<Line> index(List<Class<?>> classes, Function<String, String> sourceOf) {
        return index(classes, Collections.emptyMap(), sourceOf);
    }

    public static List<Line> index(List<Class<?>> classes, Map<String, ?> globals, Function<String, String> sourceOf) {
        Map<String, Line> result = new TreeMap<>();
        Map<Class<?>, Set<String>> originals = new HashMap<>();
        Set<Class<?>> extraTypes = new LinkedHashSet<>();
        for (Map.Entry<String, ?> global : globals.entrySet()) {
            Object value = global.getValue();
            if (value == null) continue;
            Class<?> owner = value instanceof Class<?> ? (Class<?>) value : value.getClass();
            if (!external(owner.getName())) continue;
            String origin = sourceOf.apply(owner.getName());
            put(result, new Line("+ / " + origin, owner.getName(), global.getKey() + " : " + owner.getSimpleName(), origin, true));
            extraTypes.add(owner);
        }
        for (Class<?> owner : classes) {
            if (external(owner.getName())) { extraTypes.add(owner); continue; }
            try {
                String source = sourceOf.apply(owner.getName());
                String group = "CNPC / " + owner.getPackage().getName().replace(API, "");
                Set<String> nativeMembers = originals.computeIfAbsent(owner, ScriptApiDocs::originalMembers);
                if (owner.getName().startsWith(API) && !owner.getName().startsWith(API + "wrapper.")) {
                    for (Field field : owner.getDeclaredFields()) {
                        if (!java.lang.reflect.Modifier.isPublic(field.getModifiers()) || field.isSynthetic()) continue;
                        put(result, new Line(group, owner.getName(), field.getName() + " : " + label(field.getGenericType()), source, false));
                    }
                    for (Method method : owner.getDeclaredMethods()) {
                        if (!exposed(method)) continue;
                        MixinMerged merged = method.getAnnotation(MixinMerged.class);
                        boolean extension = merged != null || nativeMembers != null && !nativeMembers.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method));
                        String origin = extension ? merged == null ? UNKNOWN : sourceOf.apply(merged.mixin()) : source;
                        put(result, new Line(extension ? "+ / " + origin : group, owner.getName(), signature(method), origin, extension));
                        if (extension) {
                            addTypes(extraTypes, method.getGenericReturnType());
                            for (Type parameter : method.getGenericParameterTypes()) addTypes(extraTypes, parameter);
                        }
                    }
                }
                Class<?> runtime = ScriptApiCompletion.runtimeWrapper(owner);
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
                    // A merged field's wrapper does not prove which addon contributed it.
                    if (field.getDeclaringClass().getName().startsWith("noppes.")) origin = UNKNOWN;
                    put(result, new Line("+ / " + origin, owner.getName(), field.getName() + " : " + label(field.getGenericType()), origin, true));
                    addTypes(extraTypes, field.getGenericType());
                }
            } catch (LinkageError | RuntimeException unavailable) { /* Broken optional API metadata. */ }
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
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }
    private static void put(Map<String, Line> lines, Line line) { lines.putIfAbsent(line.group + "\n" + line.owner + "\n" + line.signature, line); }
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
        if (type instanceof Class<?>) types.add((Class<?>) type);
        else if (type instanceof ParameterizedType) {
            ParameterizedType generic = (ParameterizedType) type;
            addTypes(types, generic.getRawType());
            for (Type argument : generic.getActualTypeArguments()) addTypes(types, argument);
        } else if (type instanceof GenericArrayType) addTypes(types, ((GenericArrayType) type).getGenericComponentType());
        else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            for (Type bound : wildcard.getUpperBounds()) addTypes(types, bound);
            for (Type bound : wildcard.getLowerBounds()) addTypes(types, bound);
        }
    }
    private static String signature(Method method) {
        StringBuilder text = new StringBuilder(method.getName()).append('(');
        Type[] params = method.getGenericParameterTypes(); Parameter[] names = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) text.append(", ");
            text.append(label(params[i])); if (names[i].isNamePresent()) text.append(' ').append(names[i].getName());
        }
        return text.append(") : ").append(label(method.getGenericReturnType())).toString();
    }
    private static String label(Type type) { return type.getTypeName().replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+", "").replace('$', '.'); }
    private static Set<String> originalMembers(Class<?> owner) {
        try (InputStream stream = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            if (stream == null) return null;
            Set<String> members = new HashSet<>();
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {
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
        String resource = name.replace('.', '/') + ".class", found = null;
        for (ModContainer mod : Loader.instance().getModList()) {
            File file = mod.getSource();
            if (file == null) continue;
            boolean present;
            try {
                if (file.isDirectory()) present = new File(file, resource).isFile();
                else try (JarFile jar = new JarFile(file)) { present = jar.getJarEntry(resource) != null; }
            } catch (IOException | SecurityException unavailable) { continue; }
            if (!present) continue;
            // Several mod containers can share one archive; report them together, never guess.
            String source = Loader.instance().getModList().stream().filter(other -> file.equals(other.getSource()))
                    .map(other -> other.getName() + " [" + other.getModId() + "]").collect(Collectors.joining(" / "));
            if (found != null && !found.equals(source)) return UNKNOWN;
            found = source;
        }
        return found == null ? UNKNOWN : found;
    }
}
