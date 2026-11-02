package bin.cnpcplus.script.client;

import bin.cnpcplus.script.ScriptLexical;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Metadata only: never constructs API objects or evaluates editor text. */
public final class ApiCompletion {
    private static final Pattern FUNCTION = Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\(\\s*([\\w$]+)[^)]*\\)\\s*\\{");
    private static final Pattern ASSIGN = Pattern.compile("\\b(?:var|let|const)\\s+([\\w$]+)\\s*=\\s*([^;\\n]+)");
    private static final Pattern NAMED_FUNCTION = Pattern.compile("\\bfunction\\s+([$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*)\\s*\\(");
    private static final Pattern HOOK_NAME = Pattern.compile("[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*");
    private static final Map<Class<?>, List<Entry>> CACHE = new HashMap<>();
    private static final String API = "noppes.npcs.api.";
    private ApiCompletion() {}

    public record Entry(String name, String insert, String signature, Type type, boolean method) {}
    public record Result(int start, List<Entry> entries) {}

    /** Hooks are the actual server-supplied Methods, including addon registrations. */
    public static Result complete(String text, int cursor, String context, List<String> hooks) {
        cursor = Math.max(0, Math.min(text.length(), cursor));
        String code = ScriptLexical.mask(text);
        if (ScriptLexical.mask(text.substring(0, cursor) + "X").charAt(cursor) != 'X')
            return new Result(cursor, List.of());
        int start = cursor;
        while (start > 0 && Character.isJavaIdentifierPart(code.charAt(start - 1))) start--;
        String prefix = code.substring(start, cursor);
        Map<String, Type> roots = roots(code, cursor, context);
        int dot = skipSpace(code, start - 1);
        List<Entry> choices = new ArrayList<>();
        if (dot >= 0 && code.charAt(dot) == '.') {
            choices.addAll(members(raw(resolve(expressionBefore(code, dot), roots, 0))));
        } else {
            boolean declaration = code.substring(0, start).matches("(?s).*\\bfunction\\s+");
            Map<String, Entry> functions = new LinkedHashMap<>();
            for (String hook : hooks) if (hook != null && HOOK_NAME.matcher(hook).matches())
                functions.put(hook, new Entry(hook, declaration ? hook : hook + "(",
                        hook + "(event) [" + ScriptApiDocs.hookSource(context, hook) + " hook]", null, !declaration));
            Matcher matcher = NAMED_FUNCTION.matcher(code);
            while (matcher.find()) {
                String name = matcher.group(1);
                functions.putIfAbsent(name, new Entry(name, declaration ? name : name + "(",
                        name + "(...) [script]", null, !declaration));
            }
            choices.addAll(functions.values());
            if (!declaration) {
                for (var root : roots.entrySet()) choices.add(new Entry(root.getKey(), root.getKey(),
                        root.getKey() + " : " + label(root.getValue()), root.getValue(), false));
                choices.add(new Entry("log", "log(", "log(Object) : void", Void.TYPE, true));
                choices.add(new Entry("dump", "dump(", "dump(Object) : String", String.class, true));
            }
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        choices.removeIf(entry -> !entry.name().toLowerCase(Locale.ROOT).startsWith(lower));
        choices.sort(Comparator.comparing((Entry entry) -> !entry.name().startsWith(prefix))
                .thenComparing(Entry::name).thenComparing(Entry::signature));
        return new Result(start, choices);
    }

    public static List<Entry> hint(String text, int cursor, String context) {
        String code = ScriptLexical.mask(text.substring(0, Math.min(cursor, text.length())));
        int depth = 0;
        for (int i = code.length() - 1; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')') depth++;
            if (c != '(') continue;
            if (depth-- > 0) continue;
            String call = expressionBefore(code, i);
            int dot = call.lastIndexOf('.');
            if (dot < 0) return List.of();
            String name = call.substring(dot + 1).trim();
            Type owner = resolve(call.substring(0, dot), roots(ScriptLexical.mask(text), cursor, context), 0);
            return members(raw(owner)).stream().filter(entry -> entry.method() && entry.name().equals(name)).toList();
        }
        return List.of();
    }

    private static Map<String, Type> roots(String code, int cursor, String context) {
        Map<String, Type> roots = new LinkedHashMap<>();
        Class<?> base = load(API + "event." + (context.equals("BlockDoorEvent") ? "BlockEvent" : context));
        String eventName = "event", function = "";
        int scope = 0;
        Matcher matcher = FUNCTION.matcher(code);
        while (matcher.find() && matcher.end() <= cursor) {
            int end = closing(code, matcher.end() - 1, '{', '}');
            if (end < 0 || cursor <= end) {
                eventName = matcher.group(2); function = matcher.group(1); scope = matcher.end();
            }
        }
        Class<?> event = eventClass(base, function);
        if (event != null) roots.put(eventName, event);
        if (context.equals("BlockEvent") || context.equals("BlockDoorEvent"))
            roots.put(eventName + ".block", load(API + "block."
                    + (context.equals("BlockDoorEvent") ? "IBlockScriptedDoor" : "IBlockScripted")));
        roots.put("PosZero", load(API + "IPos"));
        for (String name : new String[]{"AnimationType", "EntitiesType", "RoleType", "JobType", "SideType", "PotionEffectType", "ParticleType"})
            roots.put(name, load(API + "constants." + name));
        matcher = ASSIGN.matcher(code.substring(scope, Math.max(scope, Math.min(cursor, code.length()))));
        while (matcher.find()) roots.put(matcher.group(1), resolve(matcher.group(2), roots, 0));
        roots.values().removeIf(Objects::isNull);
        return roots;
    }

    private static Class<?> eventClass(Class<?> base, String function) {
        if (base == null) return null;
        String name = function.toLowerCase(Locale.ROOT);
        switch (name) {
            case "tick" -> name = "update";
            case "kill" -> name = "killedentity";
            case "collide" -> name = base.getSimpleName().equals("BlockEvent") ? "collided" : "collide";
            case "broken" -> name = "break";
            case "rainfilled" -> name = "rainfill";
            case "fallenupon" -> name = "entityfallenupon";
            case "dialog" -> { return load(API + "event.DialogEvent$OpenEvent"); }
            case "dialogoption" -> { return load(API + "event.DialogEvent$OptionEvent"); }
            case "dialogclose" -> { return load(API + "event.DialogEvent$CloseEvent"); }
            case "customguibutton" -> { return load(API + "event.CustomGuiEvent$ButtonEvent"); }
            case "customguiscroll" -> { return load(API + "event.CustomGuiEvent$ScrollEvent"); }
            case "customguiclosed" -> { return load(API + "event.CustomGuiEvent$CloseEvent"); }
            case "customguislot" -> { return load(API + "event.CustomGuiEvent$SlotEvent"); }
            case "customchestclosed" -> { return load(API + "event.CustomContainerEvent$CloseEvent"); }
            case "customchestclicked" -> { return load(API + "event.CustomContainerEvent$SlotClickedEvent"); }
            case "projectiletick" -> { return load(API + "event.ProjectileEvent$UpdateEvent"); }
            case "projectileimpact" -> { return load(API + "event.ProjectileEvent$ImpactEvent"); }
            case "queststart" -> { return load(API + "event.QuestEvent$QuestStartEvent"); }
            case "questcompleted" -> { return load(API + "event.QuestEvent$QuestCompletedEvent"); }
            case "questturnin" -> { return load(API + "event.QuestEvent$QuestTurnedInEvent"); }
            case "scriptcommand" -> { return load(API + "event.WorldEvent$ScriptCommandEvent"); }
            default -> { }
        }
        for (Class<?> nested : base.getDeclaredClasses())
            if (nested.getSimpleName().replace("Event", "").equalsIgnoreCase(name)) return nested;
        return base;
    }

    public static List<Entry> members(Class<?> owner) {
        if (owner == null || owner == Object.class || owner.isPrimitive()) return List.of();
        List<Entry> cached = CACHE.get(owner);
        if (cached != null) return cached;
        Map<String, Entry> entries = new TreeMap<>();
        collectMembers(owner, entries);
        Class<?> runtime = runtimeWrapper(owner);
        if (runtime != null) collectMembers(runtime, entries);
        if (owner.isArray()) entries.put("length", new Entry("length", "length", "length : int", Integer.TYPE, false));
        cached = List.copyOf(entries.values()); CACHE.put(owner, cached);
        return cached;
    }

    /** CNPC implementation names only; addon members are discovered, never hard-coded. */
    public static Class<?> runtimeWrapper(Class<?> owner) {
        if (owner == null) return null;
        if (owner.getName().equals(API + "NpcAPI")) return load(API + "wrapper.WrapperNpcAPI");
        if (!owner.isInterface() || !owner.getName().startsWith(API)) return null;
        String simple = owner.getSimpleName();
        if (!simple.startsWith("I")) return null;
        String wrapper = switch (simple) {
            case "ICustomNpc" -> "NPCWrapper";
            case "IEntity" -> "EntityWrapper";
            case "IEntityLiving" -> "EntityLivingBaseWrapper";
            case "IMob" -> "EntityLivingWrapper";
            case "IItemStack" -> "ItemStackWrapper";
            case "INbt" -> "NBTWrapper";
            case "IPos" -> "BlockPosWrapper";
            default -> simple.substring(1) + "Wrapper";
        };
        Class<?> runtime = load(API + "wrapper." + wrapper);
        if (runtime != null && owner.isAssignableFrom(runtime)) return runtime;
        for (Class<?> parent : owner.getInterfaces()) {
            runtime = runtimeWrapper(parent); if (runtime != null) return runtime;
        }
        return null;
    }

    private static void collectMembers(Class<?> owner, Map<String, Entry> entries) {
        Field[] fields; Method[] methods;
        try { fields = owner.getFields(); methods = owner.getMethods(); }
        catch (LinkageError | SecurityException unavailable) { return; }
        for (Field field : fields) entries.putIfAbsent("field:" + field.getName(), new Entry(field.getName(), field.getName(),
                field.getName() + " : " + label(field.getGenericType()), field.getGenericType(), false));
        for (Method method : methods) {
            if (method.getDeclaringClass() == Object.class || method.isSynthetic() || method.isBridge()) continue;
            StringBuilder signature = new StringBuilder(method.getName()).append('(');
            Type[] params = method.getGenericParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i != 0) signature.append(", ");
                signature.append(label(params[i]));
                if (method.getParameters()[i].isNamePresent()) signature.append(' ').append(method.getParameters()[i].getName());
            }
            signature.append(") : ").append(label(method.getGenericReturnType()));
            Entry entry = new Entry(method.getName(), method.getName() + (params.length == 0 ? "()" : "("),
                    signature.toString(), method.getGenericReturnType(), true);
            entries.putIfAbsent(method.getName() + Arrays.toString(method.getParameterTypes()), entry);
            if (params.length == 0 && method.getName().startsWith("get") && method.getName().length() > 3 && method.getReturnType() != Void.TYPE) {
                String property = Character.toLowerCase(method.getName().charAt(3)) + method.getName().substring(4);
                entries.putIfAbsent("field:" + property, new Entry(property, property,
                        property + " : " + label(entry.type()) + " [" + method.getName() + "()]", entry.type(), false));
            }
        }
    }

    private static Type resolve(String expression, Map<String, Type> roots, int depth) {
        if (depth > 12) return null;
        expression = expression.trim(); int i = 0;
        while (i < expression.length() && Character.isJavaIdentifierPart(expression.charAt(i))) i++;
        Type type = roots.get(expression.substring(0, i));
        while (type != null && i < expression.length()) {
            while (i < expression.length() && Character.isWhitespace(expression.charAt(i))) i++;
            if (i == expression.length()) break;
            if (expression.charAt(i) == '[') {
                int end = closing(expression, i, '[', ']'); if (end < 0) return null;
                type = element(type); i = end + 1; continue;
            }
            if (expression.charAt(i++) != '.') return null;
            while (i < expression.length() && Character.isWhitespace(expression.charAt(i))) i++;
            int start = i;
            while (i < expression.length() && Character.isJavaIdentifierPart(expression.charAt(i))) i++;
            String member = expression.substring(start, i);
            while (i < expression.length() && Character.isWhitespace(expression.charAt(i))) i++;
            boolean call = i < expression.length() && expression.charAt(i) == '(';
            Type contextual = roots.get(expression.substring(0, i).replaceAll("\\s+", ""));
            if (!call && contextual != null) { type = contextual; continue; }
            int args = -1;
            if (call) {
                int end = closing(expression, i, '(', ')'); if (end < 0) return null;
                args = argumentCount(expression.substring(i + 1, end)); i = end + 1;
            }
            if (call && member.equals("get") && type instanceof ParameterizedType && List.class.isAssignableFrom(raw(type))) {
                type = element(type); continue;
            }
            Type next = null;
            for (Entry entry : members(raw(type))) {
                if (!entry.name().equals(member) || entry.method() != call) continue;
                if (call && argumentCount(entry.signature().substring(entry.signature().indexOf('(') + 1, entry.signature().indexOf(')'))) != args) continue;
                if (next != null && !next.equals(entry.type())) return null;
                next = entry.type();
            }
            type = next;
        }
        return type;
    }
    private static Type element(Type type) {
        Class<?> raw = raw(type);
        if (raw != null && raw.isArray()) return raw.getComponentType();
        if (type instanceof ParameterizedType parameterized) return parameterized.getActualTypeArguments()[0];
        return null;
    }
    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType parameterized) return raw(parameterized.getRawType());
        if (type instanceof WildcardType wildcard) return raw(wildcard.getUpperBounds()[0]);
        if (type instanceof TypeVariable<?> variable) return raw(variable.getBounds()[0]);
        return null;
    }
    private static String label(Type type) {
        return type == null ? "?" : type.getTypeName().replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+", "").replace('$', '.');
    }
    private static Class<?> load(String name) {
        try { return Class.forName(name, false, ApiCompletion.class.getClassLoader()); }
        catch (ClassNotFoundException | LinkageError ignored) { return null; }
    }
    private static int skipSpace(String code, int i) {
        while (i >= 0 && Character.isWhitespace(code.charAt(i))) i--; return i;
    }
    private static String expressionBefore(String code, int end) {
        int i = skipSpace(code, end - 1), nesting = 0;
        for (; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')' || c == ']') nesting++;
            else if (c == '(' || c == '[') { if (nesting == 0) break; nesting--; }
            else if (nesting == 0 && !(Character.isJavaIdentifierPart(c) || c == '.' || c == ' ' || c == '\t')) break;
        }
        return code.substring(i + 1, end).trim().replaceFirst("^(?:return|throw|var|let|const)\\s+", "");
    }
    private static int closing(String code, int start, char open, char close) {
        int level = 0;
        for (int i = start; i < code.length(); i++) {
            if (code.charAt(i) == open) level++;
            if (code.charAt(i) == close && --level == 0) return i;
        }
        return -1;
    }
    private static int argumentCount(String code) {
        if (code.trim().isEmpty()) return 0;
        int count = 1, depth = 0;
        for (char c : code.toCharArray()) {
            if (c == '(' || c == '[' || c == '{' || c == '<') depth++;
            if (c == ')' || c == ']' || c == '}' || c == '>') depth--;
            if (c == ',' && depth == 0) count++;
        }
        return count;
    }
}
