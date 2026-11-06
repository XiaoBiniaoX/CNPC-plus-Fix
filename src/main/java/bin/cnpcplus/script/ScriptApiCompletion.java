package bin.cnpcplus.script;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/** Read API metadata only. Never construct an API object or evaluate editor text. */
public final class ScriptApiCompletion {
    private static final Pattern FUNCTION = Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\(\\s*([\\w$]+)[^)]*\\)\\s*\\{");
    private static final Pattern ASSIGN = Pattern.compile("\\b(?:var|let|const)\\s+([\\w$]+)\\s*=\\s*([^;\\n]+)");
    private static final Map<Class<?>, List<Entry>> CACHE = new HashMap<>();
    private static final String API = "noppes.npcs.api.";

    private ScriptApiCompletion() {}

    public static final class Entry {
        public final String name, insert, signature;
        public final Type type;
        public final boolean method;
        Entry(String name, String insert, String signature, Type type, boolean method) {
            this.name = name; this.insert = insert; this.signature = signature; this.type = type; this.method = method;
        }
    }

    public static final class Result {
        public final int start;
        public final List<Entry> entries;
        Result(int start, List<Entry> entries) { this.start = start; this.entries = entries; }
    }

    public static Result complete(String text, int cursor, String context) {
        return complete(text, cursor, context, Collections.emptyList());
    }

    public static Result complete(String text, int cursor, String context, List<String> hooks) {
        cursor = Math.max(0, Math.min(text.length(), cursor));
        String code = mask(text);
        if (mask(text.substring(0, cursor) + "X").charAt(cursor) != 'X') return new Result(cursor, Collections.emptyList());
        int start = cursor;
        while (start > 0 && identifier(code.charAt(start - 1))) start--;
        String prefix = code.substring(start, cursor);
        Map<String, Type> roots = roots(code, cursor, context);
        int dot = skipSpace(code, start - 1);
        List<Entry> choices = new ArrayList<>();
        if (dot >= 0 && code.charAt(dot) == '.') {
            Type owner = resolve(expressionBefore(code, dot), roots, 0);
            choices.addAll(members(raw(owner)));
        } else {
            boolean declaration = code.substring(0, start).matches("(?s).*\\bfunction\\s+");
            Map<String, Entry> functions = new LinkedHashMap<>();
            for (String hook : hooks) if (hook.matches("[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*"))
                functions.put(hook, new Entry(hook, declaration ? hook : hook + "(", hook + "(event) [CNPC hook]", null, !declaration));
            Matcher named = Pattern.compile("\\bfunction\\s+([$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*)\\s*\\(").matcher(code);
            while (named.find()) {
                String name = named.group(1);
                functions.putIfAbsent(name, new Entry(name, declaration ? name : name + "(", name + "(...) [script]", null, !declaration));
            }
            choices.addAll(functions.values());
            if (!declaration) {
                for (Map.Entry<String, Type> root : roots.entrySet()) if (root.getKey().indexOf('.') < 0)
                    choices.add(new Entry(root.getKey(), root.getKey(), root.getKey() + " : " + label(root.getValue()), root.getValue(), false));
                choices.add(new Entry("log", "log(", "log(Object) : void", Void.TYPE, true));
                choices.add(new Entry("dump", "dump(", "dump(Object) : String", String.class, true));
            }
        }
        choices.removeIf(e -> !e.name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)));
        choices.sort(Comparator.comparing((Entry e) -> !e.name.startsWith(prefix)).thenComparing(e -> e.name).thenComparing(e -> e.signature));
        return new Result(start, choices);
    }

    public static List<Entry> hint(String text, int cursor, String context) {
        String code = mask(text.substring(0, Math.min(cursor, text.length())));
        int depth = 0;
        for (int i = code.length() - 1; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')') depth++;
            if (c != '(') continue;
            if (depth-- > 0) continue;
            String call = expressionBefore(code, i);
            int dot = call.lastIndexOf('.');
            if (dot < 0) return Collections.emptyList();
            String name = call.substring(dot + 1).trim();
            Type owner = resolve(call.substring(0, dot), roots(mask(text), cursor, context), 0);
            List<Entry> hints = new ArrayList<>();
            for (Entry entry : members(raw(owner))) if (entry.method && entry.name.equals(name)) hints.add(entry);
            return hints;
        }
        return Collections.emptyList();
    }

    private static Map<String, Type> roots(String code, int cursor, String context) {
        Map<String, Type> roots = new LinkedHashMap<>();
        Class<?> base = load(API + "event." + (context.equals("BlockDoorEvent") ? "BlockEvent" : context));
        String eventName = "event", function = "";
        int scope = 0;
        Matcher m = FUNCTION.matcher(code);
        while (m.find() && m.end() <= cursor) {
            int end = closing(code, m.end() - 1, '{', '}');
            if (end < 0 || cursor <= end) { eventName = m.group(2); function = m.group(1); scope = m.end(); }
        }
        Class<?> event = eventClass(base, function);
        if (event != null) roots.put(eventName, event);
        if (context.equals("BlockEvent") || context.equals("BlockDoorEvent"))
            roots.put(eventName + ".block", load(API + "block." + (context.equals("BlockDoorEvent") ? "IBlockScriptedDoor" : "IBlockScripted")));
        roots.put("PosZero", load(API + "IPos"));
        roots.put("CNPCPlusLog", org.apache.logging.log4j.Logger.class);
        for (String name : new String[]{"AnimationType", "EntityType", "RoleType", "JobType", "SideType", "TacticalType", "ParticleType"}) roots.put(name, load(API + "constants." + name));
        // ponytail: local declarations and API chains, not a JS type checker. Unknown/dynamic values stay unknown.
        m = ASSIGN.matcher(code.substring(scope, Math.max(scope, Math.min(cursor, code.length()))));
        while (m.find()) {
            Type type = resolve(m.group(2), roots, 0);
            roots.put(m.group(1), type);
        }
        roots.values().removeIf(Objects::isNull);
        return roots;
    }

    private static Class<?> eventClass(Class<?> base, String function) {
        if (base == null) return null;
        String name = function.toLowerCase(Locale.ROOT);
        switch (name) {
            case "tick": name = "update"; break;
            case "kill": name = "killedentity"; break;
            case "collide": name = base.getSimpleName().equals("BlockEvent") ? "collided" : "collide"; break;
            case "broken": name = "break"; break;
            case "rainfilled": name = "rainfill"; break;
            case "fallenupon": name = "entityfallenupon"; break;
            case "dialog": return load(API + "event.DialogEvent$OpenEvent");
            case "dialogoption": return load(API + "event.DialogEvent$OptionEvent");
            case "dialogclose": return load(API + "event.DialogEvent$CloseEvent");
            case "customguibutton": return load(API + "event.CustomGuiEvent$ButtonEvent");
            case "customguiscroll": return load(API + "event.CustomGuiEvent$ScrollEvent");
            case "customguiclosed": return load(API + "event.CustomGuiEvent$CloseEvent");
            case "customguislot": return load(API + "event.CustomGuiEvent$SlotEvent");
            case "customchestclosed": return load(API + "event.CustomContainerEvent$CloseEvent");
            case "customchestclicked": return load(API + "event.CustomContainerEvent$SlotClickedEvent");
            case "projectiletick": return load(API + "event.ProjectileEvent$UpdateEvent");
            case "projectileimpact": return load(API + "event.ProjectileEvent$ImpactEvent");
            case "queststart": return load(API + "event.QuestEvent$QuestStartEvent");
            case "questcompleted": return load(API + "event.QuestEvent$QuestCompletedEvent");
            case "questturnin": return load(API + "event.QuestEvent$QuestTurnedInEvent");
            case "scriptcommand": return load(API + "event.WorldEvent$ScriptCommandEvent");
            case "tossed": name = "tossed"; break;
        }
        for (Class<?> nested : base.getDeclaredClasses()) if (nested.getSimpleName().replace("Event", "").equalsIgnoreCase(name)) return nested;
        return base;
    }

    public static List<Entry> members(Class<?> owner) {
        if (owner == null || owner == Object.class || owner.isPrimitive()) return Collections.emptyList();
        List<Entry> cached = CACHE.get(owner);
        if (cached != null) return cached;
        Map<String, Entry> entries = new TreeMap<>();
        collectMembers(owner, entries);
        Class<?> runtime = runtimeWrapper(owner);
        if (runtime != null) collectMembers(runtime, entries);
        if (owner.isArray()) entries.put("length", new Entry("length", "length", "length : int", Integer.TYPE, false));
        cached = Collections.unmodifiableList(new ArrayList<>(entries.values()));
        CACHE.put(owner, cached);
        return cached;
    }

    /** CNPC naming only. Addon public methods are reflected after Mixin transformation. */
    public static Class<?> runtimeWrapper(Class<?> owner) {
        if (owner == null) return null;
        if (owner.getName().equals(API + "NpcAPI")) return load(API + "wrapper.WrapperNpcAPI");
        if (!owner.isInterface() || !owner.getName().startsWith(API) || !owner.getSimpleName().startsWith("I")) return null;
        String wrapper;
        switch (owner.getSimpleName()) {
            case "ICustomNpc": wrapper = "NPCWrapper"; break;
            case "IEntity": wrapper = "EntityWrapper"; break;
            case "IEntityLiving": wrapper = "EntityLivingBaseWrapper"; break;
            case "IMob": wrapper = "EntityLivingWrapper"; break;
            case "IItemStack": wrapper = "ItemStackWrapper"; break;
            case "INbt": wrapper = "NBTWrapper"; break;
            case "IPos": wrapper = "BlockPosWrapper"; break;
            case "INPCDisplay": return load("noppes.npcs.entity.data.DataDisplay");
            case "INPCStats": return load("noppes.npcs.entity.data.DataStats");
            default: wrapper = owner.getSimpleName().substring(1) + "Wrapper";
        }
        Class<?> runtime = load(API + "wrapper." + wrapper);
        if (runtime != null && owner.isAssignableFrom(runtime)) return runtime;
        for (Class<?> parent : owner.getInterfaces()) {
            runtime = runtimeWrapper(parent);
            if (runtime != null) return runtime;
        }
        return null;
    }

    private static void collectMembers(Class<?> owner, Map<String, Entry> entries) {
        Field[] fields; Method[] methods;
        try { fields = owner.getFields(); methods = owner.getMethods(); }
        catch (LinkageError | SecurityException unavailable) { return; }
        for (Field field : fields) {
            Entry e = new Entry(field.getName(), field.getName(), field.getName() + " : " + label(field.getGenericType()), field.getGenericType(), false);
            entries.putIfAbsent("field:" + field.getName(), e);
        }
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
            Entry e = new Entry(method.getName(), method.getName() + (params.length == 0 ? "()" : "("), signature.toString(), method.getGenericReturnType(), true);
            String key = method.getName() + Arrays.toString(method.getParameterTypes());
            entries.putIfAbsent(key, e);
            if (params.length == 0 && method.getName().startsWith("get") && method.getName().length() > 3 && method.getReturnType() != Void.TYPE) {
                String property = Character.toLowerCase(method.getName().charAt(3)) + method.getName().substring(4);
                Entry p = new Entry(property, property, property + " : " + label(e.type) + " [" + method.getName() + "()]", e.type, false);
                entries.putIfAbsent("field:" + property, p);
            }
        }
    }

    private static Type resolve(String expression, Map<String, Type> roots, int depth) {
        if (depth > 12) return null;
        expression = expression.trim();
        int i = 0;
        while (i < expression.length() && identifier(expression.charAt(i))) i++;
        Type type = roots.get(expression.substring(0, i));
        // Only the current event's block is known to be scripted, not every world block.
        for (Map.Entry<String, Type> root : roots.entrySet()) if (root.getKey().indexOf('.') >= 0
                && expression.startsWith(root.getKey()) && (expression.length() == root.getKey().length()
                || !identifier(expression.charAt(root.getKey().length())))) {
            type = root.getValue(); i = root.getKey().length(); break;
        }
        while (type != null && i < expression.length()) {
            while (i < expression.length() && Character.isWhitespace(expression.charAt(i))) i++;
            if (i == expression.length()) break;
            if (expression.charAt(i) == '[') {
                int end = closing(expression, i, '[', ']');
                if (end < 0) return null;
                type = element(type); i = end + 1; continue;
            }
            if (expression.charAt(i++) != '.') return null;
            while (i < expression.length() && Character.isWhitespace(expression.charAt(i))) i++;
            int start = i;
            while (i < expression.length() && identifier(expression.charAt(i))) i++;
            String member = expression.substring(start, i);
            while (i < expression.length() && Character.isWhitespace(expression.charAt(i))) i++;
            boolean call = i < expression.length() && expression.charAt(i) == '(';
            int args = -1;
            if (call) {
                int end = closing(expression, i, '(', ')');
                if (end < 0) return null;
                args = argumentCount(expression.substring(i + 1, end)); i = end + 1;
            }
            if (call && member.equals("get") && type instanceof ParameterizedType && List.class.isAssignableFrom(raw(type))) { type = element(type); continue; }
            Type next = null;
            for (Entry e : members(raw(type))) if (e.name.equals(member) && e.method == call) {
                if (call && argumentCount(e.signature.substring(e.signature.indexOf('(') + 1, e.signature.indexOf(')'))) != args) continue;
                if (next != null && !next.equals(e.type)) return null; // Ambiguous overload return type: do not guess.
                next = e.type;
            }
            type = next;
        }
        return type;
    }

    private static Type element(Type type) {
        Class<?> raw = raw(type);
        if (raw != null && raw.isArray()) return raw.getComponentType();
        if (type instanceof ParameterizedType) return ((ParameterizedType) type).getActualTypeArguments()[0];
        return null;
    }
    private static Class<?> raw(Type type) {
        if (type instanceof Class) return (Class<?>) type;
        if (type instanceof ParameterizedType) return raw(((ParameterizedType) type).getRawType());
        if (type instanceof WildcardType) return raw(((WildcardType) type).getUpperBounds()[0]);
        if (type instanceof TypeVariable) return raw(((TypeVariable<?>) type).getBounds()[0]);
        return null;
    }
    private static String label(Type type) { return type == null ? "?" : type.getTypeName().replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+", "").replace('$', '.'); }
    private static Class<?> load(String name) {
        try { return Class.forName(name, false, ScriptApiCompletion.class.getClassLoader()); }
        catch (ClassNotFoundException | LinkageError error) { return null; }
    }
    private static boolean identifier(char c) { return Character.isJavaIdentifierPart(c); }
    private static int skipSpace(String code, int i) { while (i >= 0 && Character.isWhitespace(code.charAt(i))) i--; return i; }
    private static String expressionBefore(String code, int end) {
        int i = skipSpace(code, end - 1), nesting = 0;
        for (; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')' || c == ']') nesting++;
            else if (c == '(' || c == '[') { if (nesting == 0) break; nesting--; }
            else if (nesting == 0 && !(identifier(c) || c == '.' || c == ' ' || c == '\t')) break;
        }
        String expression = code.substring(i + 1, end).trim();
        // Do not swallow a preceding return/var keyword through whitespace.
        return expression.replaceFirst("^(?:return|throw|var|let|const)\\s+", "");
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

    /** Preserve offsets, including regex literals and template interpolation code. */
    public static String mask(String text) {
        return ScriptLexical.mask(text);
    }
}
