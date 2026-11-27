package bin.cnpcplus.api.script;

import bin.cnpcplus.script.ScriptApiCompletion;
import bin.cnpcplus.script.ScriptHooks;
import bin.cnpcplus.script.tools.ScriptApiRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import noppes.npcs.controllers.ScriptContainer;

/** Immutable, non-executing metadata for script editors and checkers. */
public final class ScriptMetadataApi {
    public static final String UNKNOWN = "UNKNOWN";

    public static final class MetadataContext {
        private final String contextKind, languageId;
        private final Set<String> declaredGlobals;

        public MetadataContext(String contextKind, String languageId, Set<String> declaredGlobals) {
            this.contextKind = contextKind == null ? UNKNOWN : contextKind;
            this.languageId = languageId == null ? "" : languageId;
            this.declaredGlobals = Collections.unmodifiableSet(new LinkedHashSet<>(declaredGlobals == null
                    ? Collections.<String>emptySet() : declaredGlobals));
        }

        public String contextKind() { return contextKind; }
        public String languageId() { return languageId; }
        public Set<String> declaredGlobals() { return declaredGlobals; }
    }

    public static final class GlobalInfo {
        private final String name, typeName, genericSignature, description, source;
        private final boolean readOnly, invocationOnly;

        public GlobalInfo(String name, String typeName, String genericSignature, boolean readOnly,
                boolean invocationOnly, String description, String source) {
            this.name = name; this.typeName = typeName == null ? UNKNOWN : typeName;
            this.genericSignature = genericSignature == null ? UNKNOWN : genericSignature;
            this.readOnly = readOnly; this.invocationOnly = invocationOnly;
            this.description = description == null ? "" : description;
            this.source = source == null ? UNKNOWN : source;
        }

        public String name() { return name; }
        public String typeName() { return typeName; }
        public String genericSignature() { return genericSignature; }
        public boolean readOnly() { return readOnly; }
        public boolean invocationOnly() { return invocationOnly; }
        public String description() { return description; }
        public String source() { return source; }
    }

    public static final class HookInfo {
        private final String hookName, contextKind, eventTypeName, source;
        private final boolean eventTypeKnown, cancellable;
        private final List<String> writableFields;

        private HookInfo(String hookName, String contextKind, Class<?> eventType) {
            this.hookName = hookName; this.contextKind = contextKind;
            this.eventTypeKnown = eventType != null;
            this.eventTypeName = eventType == null ? UNKNOWN : eventType.getName();
            this.source = eventType == null ? UNKNOWN : "CNPCplus/CNPC";
            this.cancellable = eventType != null && eventType.isAnnotationPresent(Cancelable.class);
            List<String> writable = new ArrayList<>();
            if (eventType != null) for (Field field : eventType.getFields())
                if (!Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) writable.add(field.getName());
            Collections.sort(writable);
            this.writableFields = Collections.unmodifiableList(writable);
        }

        public String hookName() { return hookName; }
        public String contextKind() { return contextKind; }
        public String eventTypeName() { return eventTypeName; }
        public boolean eventTypeKnown() { return eventTypeKnown; }
        public String source() { return source; }
        public boolean cancellable() { return cancellable; }
        public List<String> writableFields() { return writableFields; }
    }

    public static final class ParameterInfo {
        private final String name, typeName, genericSignature;
        ParameterInfo(String name, Type type) {
            this.name = name == null ? "" : name;
            this.typeName = type instanceof Class<?> ? ((Class<?>) type).getName() : UNKNOWN;
            this.genericSignature = type == null ? UNKNOWN : type.getTypeName();
        }
        public String name() { return name; }
        public String typeName() { return typeName; }
        public String genericSignature() { return genericSignature; }
    }

    public static final class MemberInfo {
        private final String name, kind, typeName, genericSignature, lifecycle, threadPolicy;
        private final boolean staticMember, writable, varargs, deprecated;
        private final List<ParameterInfo> parameters;

        private MemberInfo(String name, String kind, Type type, int modifiers, boolean writable,
                boolean varargs, boolean deprecated, List<ParameterInfo> parameters, String lifecycle, String threadPolicy) {
            this.name = name; this.kind = kind;
            this.typeName = type instanceof Class<?> ? ((Class<?>) type).getName() : UNKNOWN;
            this.genericSignature = type == null ? UNKNOWN : type.getTypeName();
            this.staticMember = Modifier.isStatic(modifiers); this.writable = writable;
            this.varargs = varargs; this.deprecated = deprecated;
            this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
            this.lifecycle = lifecycle; this.threadPolicy = threadPolicy;
        }

        public String name() { return name; }
        public String kind() { return kind; }
        public String typeName() { return typeName; }
        public String genericSignature() { return genericSignature; }
        public boolean staticMember() { return staticMember; }
        public boolean writable() { return writable; }
        public boolean varargs() { return varargs; }
        public boolean deprecated() { return deprecated; }
        public List<ParameterInfo> parameters() { return parameters; }
        public String lifecycle() { return lifecycle; }
        public String threadPolicy() { return threadPolicy; }
    }

    public static final class TypeInfo {
        private final String binaryName, displaySignature, status;
        private final List<String> parents, nestedTypes;
        private final List<MemberInfo> constructors, members;

        private TypeInfo(String binaryName, String displaySignature, String status, List<String> parents,
                List<String> nestedTypes, List<MemberInfo> constructors, List<MemberInfo> members) {
            this.binaryName = binaryName; this.displaySignature = displaySignature; this.status = status;
            this.parents = immutable(parents); this.nestedTypes = immutable(nestedTypes);
            this.constructors = Collections.unmodifiableList(new ArrayList<>(constructors));
            this.members = Collections.unmodifiableList(new ArrayList<>(members));
        }

        public String binaryName() { return binaryName; }
        public String displaySignature() { return displaySignature; }
        public String status() { return status; }
        public List<String> parents() { return parents; }
        public List<String> nestedTypes() { return nestedTypes; }
        public List<MemberInfo> constructors() { return constructors; }
        public List<MemberInfo> members() { return members; }
    }

    public static final class RegisteredGlobal {
        private final GlobalInfo info;
        private RegisteredGlobal(GlobalInfo info) { this.info = info; }
        public GlobalInfo info() { return info; }
    }

    private static final Map<String, GlobalInfo> ADDON_GLOBALS = new ConcurrentHashMap<>();

    private ScriptMetadataApi() {}

    public static MetadataContext context(ScriptPage page, Set<String> declaredGlobals) {
        if (page == null) throw new IllegalArgumentException("page");
        return new MetadataContext(page.contextKind(), page.effectiveLanguageId(), declaredGlobals);
    }

    public static List<GlobalInfo> globals(MetadataContext context) {
        if (context == null) throw new IllegalArgumentException("context");
        Map<String, GlobalInfo> result = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> root : ScriptApiRegistry.roots().entrySet())
            result.put(root.getKey(), new GlobalInfo(root.getKey(), root.getValue().getName(), root.getValue().getTypeName(),
                    true, false, "CNPCplus script API", "CNPCplus"));
        result.put("event", new GlobalInfo("event", UNKNOWN, UNKNOWN, true, true,
                "The current hook event; absent from delayed callbacks", "CNPC"));
        result.put("log", new GlobalInfo("log", "java.util.function.Consumer", UNKNOWN, true, false,
                "Script output binding", "CNPC"));
        result.putAll(ADDON_GLOBALS);
        for (String name : context.declaredGlobals()) result.putIfAbsent(name,
                new GlobalInfo(name, UNKNOWN, UNKNOWN, true, false, "Addon-declared global", "addon"));
        List<GlobalInfo> values = new ArrayList<>(result.values());
        values.sort(Comparator.comparing(GlobalInfo::name));
        return Collections.unmodifiableList(values);
    }

    public static void registerGlobal(GlobalInfo info) {
        if (info == null || info.name() == null || !info.name().matches("[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*"))
            throw new IllegalArgumentException("global info");
        ADDON_GLOBALS.put(info.name(), info);
        ScriptCheckApi.registerGlobal(info.name());
    }

    public static void unregisterGlobal(String name) {
        if (name == null) return;
        ADDON_GLOBALS.remove(name);
        ScriptCheckApi.unregisterGlobal(name);
    }

    public static List<HookInfo> hooks(MetadataContext context) {
        if (context == null) throw new IllegalArgumentException("context");
        List<HookInfo> result = new ArrayList<>();
        for (String name : ScriptHooks.names(null, context.contextKind()))
            result.add(new HookInfo(name, context.contextKind(), ScriptApiCompletion.hookEventType(context.contextKind(), name)));
        result.sort(Comparator.comparing(HookInfo::hookName));
        return Collections.unmodifiableList(result);
    }

    /** Loads without class initialization and reflects public members only. */
    public static TypeInfo describeType(String binaryName) {
        if (binaryName == null || binaryName.trim().isEmpty()) throw new IllegalArgumentException("binaryName");
        try {
            Class<?> type = Class.forName(binaryName, false, ScriptMetadataApi.class.getClassLoader());
            return describe(type);
        } catch (ClassNotFoundException | LinkageError | SecurityException unavailable) {
            return new TypeInfo(binaryName, UNKNOWN, "NOT_FOUND", Collections.<String>emptyList(),
                    Collections.<String>emptyList(), Collections.<MemberInfo>emptyList(), Collections.<MemberInfo>emptyList());
        }
    }

    private static TypeInfo describe(Class<?> type) {
        List<String> parents = new ArrayList<>();
        if (type.getSuperclass() != null) parents.add(type.getSuperclass().getName());
        for (Class<?> parent : type.getInterfaces()) parents.add(parent.getName());
        Collections.sort(parents);
        List<String> nested = new ArrayList<>();
        for (Class<?> child : type.getClasses()) nested.add(child.getName());
        Collections.sort(nested);
        List<MemberInfo> constructors = new ArrayList<>(), members = new ArrayList<>();
        for (Constructor<?> constructor : type.getConstructors()) {
            if (!Modifier.isPublic(constructor.getModifiers())) continue;
            constructors.add(new MemberInfo(type.getSimpleName(), "CONSTRUCTOR", type, constructor.getModifiers(), false,
                    constructor.isVarArgs(), constructor.isAnnotationPresent(Deprecated.class), parameters(constructor.getParameters(), constructor.getGenericParameterTypes()), UNKNOWN, UNKNOWN));
        }
        for (Field field : type.getFields()) if (Modifier.isPublic(field.getModifiers()))
            members.add(new MemberInfo(field.getName(), "FIELD", field.getGenericType(), field.getModifiers(),
                    !Modifier.isFinal(field.getModifiers()), false, field.isAnnotationPresent(Deprecated.class),
                    Collections.<ParameterInfo>emptyList(), lifecycle(field), threadPolicy(field)));
        for (Method method : type.getMethods()) if (Modifier.isPublic(method.getModifiers())
                && method.getDeclaringClass() != Object.class && !method.isSynthetic() && !method.isBridge())
            members.add(new MemberInfo(method.getName(), "METHOD", method.getGenericReturnType(), method.getModifiers(), false,
                    method.isVarArgs(), method.isAnnotationPresent(Deprecated.class),
                    parameters(method.getParameters(), method.getGenericParameterTypes()), lifecycle(method), threadPolicy(method)));
        Comparator<MemberInfo> order = Comparator.comparing(MemberInfo::name).thenComparing(MemberInfo::kind)
                .thenComparing(MemberInfo::genericSignature);
        constructors.sort(order); members.sort(order);
        return new TypeInfo(type.getName(), type.getTypeName(), "SUCCESS", parents, nested, constructors, members);
    }

    private static List<ParameterInfo> parameters(java.lang.reflect.Parameter[] names, Type[] types) {
        List<ParameterInfo> result = new ArrayList<>();
        for (int i = 0; i < types.length; i++) result.add(new ParameterInfo(names[i].isNamePresent() ? names[i].getName() : "", types[i]));
        return result;
    }

    private static String lifecycle(java.lang.reflect.AnnotatedElement element) {
        for (ScriptApiRegistry.Entry entry : ScriptApiRegistry.entries())
            if (element instanceof Method && entry.publicName().endsWith("." + ((Method) element).getName())) return entry.lifecycle();
        return UNKNOWN;
    }

    private static String threadPolicy(java.lang.reflect.AnnotatedElement element) {
        if (!(element instanceof Method)) return UNKNOWN;
        for (ScriptApiRegistry.Entry entry : ScriptApiRegistry.entries())
            if (entry.publicName().endsWith("." + ((Method) element).getName())) return entry.threadPolicy().name();
        return UNKNOWN;
    }

    private static List<String> immutable(List<String> values) { return Collections.unmodifiableList(new ArrayList<>(values)); }
}
