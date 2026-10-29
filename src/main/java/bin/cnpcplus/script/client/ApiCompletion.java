package bin.cnpcplus.script.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 有限静态推断：只读实际安装版本的API成员，不运行编辑中的代码。 */
public final class ApiCompletion {
    public record Entry(String name, String signature, boolean method, int parameters) {}
    public record Result(int start, int end, List<Entry> entries) {}
    public record CallTip(List<Entry> entries, int argument) {}
    private static final Pattern FUNCTION = Pattern.compile("function\\s+(\\w+)\\s*\\(\\s*([\\w$]+)");
    private static final Pattern RECEIVER = Pattern.compile("([\\w$]+(?:\\.[\\w$]+(?:\\([^()\\n]*\\))?(?:\\[\\d+\\])?)*)\\.$");
    private static final Map<Class<?>, List<Entry>> MEMBERS = new HashMap<>();
    private static final Map<String, String> EVENTS = Map.ofEntries(
            Map.entry("tick", "Update"), Map.entry("kill", "KilledEntity"),
            Map.entry("targetLost", "TargetLost"), Map.entry("rangedLaunched", "RangedLaunched"),
            Map.entry("broken", "Break"), Map.entry("fallenUpon", "EntityFallenUpon"),
            Map.entry("rainFilled", "RainFill"), Map.entry("neighborChanged", "NeighborChanged"),
            Map.entry("doorToggle", "DoorToggle"));

    private ApiCompletion() {}

    public static Result complete(String source, int cursor, String family) {
        cursor = Math.max(0, Math.min(cursor, source.length()));
        String code = codePrefix(source, cursor, false);
        if (code == null) return new Result(cursor, cursor, List.of());
        int start = cursor;
        while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) start--;
        int end = cursor;
        while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) end++;
        String prefix = source.substring(start, cursor);
        Matcher receiver = RECEIVER.matcher(code.substring(0, start));
        if (!receiver.find()) return new Result(start, cursor, List.of());
        Class<?> type = resolve(receiver.group(1), code.substring(0, start), family, 0);
        if (type == null || !type.getName().startsWith("noppes.npcs.api.")) {
            return new Result(start, cursor, List.of());
        }
        List<Entry> entries = MEMBERS.computeIfAbsent(type, ApiCompletion::members).stream()
                .filter(entry -> entry.name.startsWith(prefix)).toList();
        return new Result(start, end, entries);
    }

    public static CallTip signature(String source, int cursor, String family) {
        String code = codePrefix(source, Math.max(0, Math.min(cursor, source.length())), true);
        if (code == null) return null;
        int depth = 0, argument = 0;
        for (int i = code.length() - 1; i >= 0; i--) {
            char ch = code.charAt(i);
            if (ch == ')' || ch == ']' || ch == '}') depth++;
            else if (ch == '(' && depth == 0) {
                Result result = complete(source, i, family);
                String name = source.substring(result.start(), i);
                List<Entry> entries = result.entries().stream()
                        .filter(entry -> entry.method() && entry.name().equals(name)).toList();
                return entries.isEmpty() ? null : new CallTip(entries, argument + 1);
            } else if (ch == '(' || ch == '[' || ch == '{') depth--;
            else if (ch == ',' && depth == 0) argument++;
        }
        return null;
    }

    // ponytail: lexical masking, not a JS/Lua compiler. Dynamic assignments and template expressions stay unsupported.
    private static String codePrefix(String source, int end, boolean inArgument) {
        StringBuilder code = new StringBuilder(source.substring(0, end));
        char quote = 0;
        boolean lineComment = false, blockComment = false;
        for (int i = 0; i < end; i++) {
            char ch = source.charAt(i), next = i + 1 < end ? source.charAt(i + 1) : 0;
            if (lineComment) {
                if (ch == '\n') lineComment = false;
                else code.setCharAt(i, ' ');
            } else if (blockComment) {
                if (ch != '\n') code.setCharAt(i, ' ');
                if (ch == '*' && next == '/') { code.setCharAt(++i, ' '); blockComment = false; }
            } else if (quote != 0) {
                if (ch != '\n') code.setCharAt(i, ' ');
                if (ch == '\\' && i + 1 < end) code.setCharAt(++i, ' ');
                else if (ch == quote) quote = 0;
            } else if (ch == '/' && next == '/') {
                code.setCharAt(i, ' '); code.setCharAt(++i, ' '); lineComment = true;
            } else if (ch == '/' && next == '*') {
                code.setCharAt(i, ' '); code.setCharAt(++i, ' '); blockComment = true;
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                code.setCharAt(i, ' '); quote = ch;
            }
        }
        return quote != 0 && !inArgument || lineComment || blockComment ? null : code.toString();
    }

    private static List<Entry> members(Class<?> type) {
        List<Entry> entries = new ArrayList<>();
        for (Field field : type.getFields()) {
            if (field.isSynthetic()) continue;
            entries.add(new Entry(field.getName(), field.getName() + " : " + simple(field.getType()), false, 0));
        }
        for (Method method : type.getMethods()) {
            if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers())
                    || method.getDeclaringClass() == Object.class) continue;
            String parameters = String.join(", ", Arrays.stream(method.getParameterTypes())
                    .map(ApiCompletion::simple).toList());
            entries.add(new Entry(method.getName(), method.getName() + "(" + parameters + ") : "
                    + simple(method.getReturnType()), true, method.getParameterCount()));
        }
        return entries.stream().distinct().sorted(Comparator.comparing(Entry::name)
                .thenComparing(Entry::signature)).toList();
    }

    private static String simple(Class<?> type) {
        return type.isArray() ? simple(type.getComponentType()) + "[]" : type.getSimpleName();
    }

    private static Class<?> resolve(String expression, String source, String family, int depth) {
        if (depth > 6) return null;
        List<String> parts = split(expression);
        String root = parts.getFirst();
        Matcher function = FUNCTION.matcher(source);
        String functionName = "", parameter = "event";
        while (function.find()) { functionName = function.group(1); parameter = function.group(2); }
        Class<?> type = null;
        if (root.equals(parameter)) {
            type = eventType(family, functionName);
        } else {
            Matcher alias = Pattern.compile("(?:var|let|const)\\s+" + Pattern.quote(root)
                    + "\\s*=\\s*([^;\\n]+)").matcher(source);
            String value = null;
            while (alias.find()) value = alias.group(1).trim();
            if (value != null) type = resolve(value, source, family, depth + 1);
        }
        for (int i = 1; type != null && i < parts.size(); i++) {
            String part = parts.get(i);
            boolean index = part.matches(".*\\[\\d+\\]$");
            part = part.replaceFirst("\\[\\d+\\]$", "");
            int paren = part.indexOf('(');
            if (paren >= 0) {
                String name = part.substring(0, paren);
                Class<?> result = null;
                for (Method method : type.getMethods()) {
                    if (!method.getName().equals(name) || method.isBridge()) continue;
                    if (result != null && result != method.getReturnType()) return null; // 不猜歧义重载。
                    result = method.getReturnType();
                }
                type = result;
            } else {
                try { type = type.getField(part).getType(); }
                catch (NoSuchFieldException ignored) { return null; }
            }
            if (index) type = type != null && type.isArray() ? type.getComponentType() : null;
        }
        return type;
    }

    private static List<String> split(String expression) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(' || c == '[') depth++;
            if (c == ')' || c == ']') depth--;
            if (c == '.' && depth == 0) { parts.add(expression.substring(start, i)); start = i + 1; }
        }
        parts.add(expression.substring(start));
        return parts;
    }

    private static Class<?> eventType(String family, String function) {
        String base = "noppes.npcs.api.event." + family + "Event";
        String event = EVENTS.getOrDefault(function, function.isEmpty() ? "" :
                Character.toUpperCase(function.charAt(0)) + function.substring(1));
        try {
            try { return Class.forName(base + "$" + event + "Event", false, ApiCompletion.class.getClassLoader()); }
            catch (ClassNotFoundException ignored) { return Class.forName(base, false, ApiCompletion.class.getClassLoader()); }
        } catch (ClassNotFoundException ignored) { return null; }
    }
}
