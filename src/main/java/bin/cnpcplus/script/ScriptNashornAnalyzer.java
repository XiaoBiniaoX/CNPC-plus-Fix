package bin.cnpcplus.script;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Optional reflective Nashorn parser access. Parsing never evaluates a script. */
final class ScriptNashornAnalyzer {
    record Result(boolean available, Object tree, List<ScriptDiagnostic> diagnostics) {}
    private ScriptNashornAnalyzer() {}

    static Result parse(String source) {
        var diagnostics = new ArrayList<ScriptDiagnostic>();
        for (ClassLoader loader : loaders()) {
            try {
                Class<?> parserType = Class.forName("org.openjdk.nashorn.api.tree.Parser", false, loader);
                Class<?> listenerType = Class.forName("org.openjdk.nashorn.api.tree.DiagnosticListener", false, loader);
                Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                        (proxy, method, args) -> {
                            if (method.getName().equals("report") && args != null && args.length == 1)
                                diagnostics.add(fromDiagnostic(args[0], source));
                            if (method.getName().equals("toString")) return "CNPCPlusScriptCheckListener";
                            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                            if (method.getName().equals("equals")) return proxy == args[0];
                            return null;
                        });
                Object parser = parserType.getMethod("create", String[].class).invoke(null,
                        (Object)new String[]{"-strict"});
                Method parse = java.util.Arrays.stream(parserType.getMethods())
                        .filter(method -> method.getName().equals("parse") && method.getParameterCount() == 3
                                && method.getParameterTypes()[0] == String.class && method.getParameterTypes()[1] == String.class)
                        .findFirst().orElseThrow();
                Object tree = null;
                try { tree = parse.invoke(parser, "<CNPC script check>", source, listener); }
                catch (InvocationTargetException error) { addThrown(error.getCause(), source, diagnostics); }
                return new Result(true, tree, List.copyOf(diagnostics));
            } catch (ClassNotFoundException ignored) {
                // Nashorn is optional on the editor/test classpath.
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // An incompatible optional parser must not prevent opening the script editor.
            }
        }
        return new Result(false, null, List.of());
    }

    static List<Object> nodes(Object root) {
        if (root == null) return List.of();
        var nodes = new ArrayList<Object>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        walk(root, nodes, seen);
        return nodes;
    }
    static boolean is(Object node, String interfaceName) {
        return node != null && interfaces(node.getClass()).stream().anyMatch(type -> type.getSimpleName().equals(interfaceName));
    }
    static Object get(Object node, String name) {
        if (node == null) return null;
        for (Class<?> type : interfaces(node.getClass())) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    try { return method.invoke(node); }
                    catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
                }
            }
        }
        return null;
    }
    static List<?> list(Object node, String name) {
        Object result = get(node, name);
        return result instanceof List<?> values ? values : List.of();
    }
    static String kind(Object node) {
        Object kind = get(node, "getKind");
        return kind == null ? "" : kind.toString();
    }
    static int start(Object node) {
        Object value = get(node, "getStartPosition");
        return value instanceof Number number ? (int)Math.max(0, number.longValue()) : 0;
    }
    static int end(Object node, int sourceLength) {
        Object value = get(node, "getEndPosition");
        return value instanceof Number number ? (int)Math.max(start(node), Math.min(sourceLength, number.longValue())) : start(node);
    }
    static String identifier(Object node) {
        Object name = get(node, "getName");
        Object value = name instanceof String ? name : get(name, "getName");
        return value == null ? "" : value.toString();
    }
    private static void walk(Object node, List<Object> nodes, Set<Object> seen) {
        if (node == null || !seen.add(node)) return;
        nodes.add(node);
        for (Class<?> type : interfaces(node.getClass())) {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() != 0 || !(method.getName().startsWith("get") || method.getName().startsWith("is"))) continue;
                try {
                    Object child = method.invoke(node);
                    if (child instanceof Iterable<?> values) {
                        for (Object value : values) if (isTree(value)) walk(value, nodes, seen);
                    } else if (isTree(child)) walk(child, nodes, seen);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Some optional tree properties are absent on a particular Nashorn revision.
                }
            }
        }
    }
    private static boolean isTree(Object value) {
        return value != null && interfaces(value.getClass()).stream().anyMatch(type -> type.getSimpleName().equals("Tree"));
    }
    private static Set<Class<?>> interfaces(Class<?> type) {
        Set<Class<?>> found = new java.util.LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) collect(current, found);
        return found;
    }
    private static void collect(Class<?> type, Set<Class<?>> found) {
        for (Class<?> contract : type.getInterfaces()) if (found.add(contract)) collect(contract, found);
    }
    private static List<ClassLoader> loaders() {
        var loaders = new ArrayList<ClassLoader>();
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) loaders.add(context);
        ClassLoader own = ScriptNashornAnalyzer.class.getClassLoader();
        if (own != null && !loaders.contains(own)) loaders.add(own);
        return loaders;
    }
    private static ScriptDiagnostic fromDiagnostic(Object diagnostic, String source) {
        long line = number(diagnostic, "getLineNumber", 0);
        long column = number(diagnostic, "getColumnNumber", 0);
        long position = number(diagnostic, "getPosition", -1);
        String kind = String.valueOf(invoke(diagnostic, "getKind"));
        String message = String.valueOf(invoke(diagnostic, "getMessage"));
        int offset = position >= 0 ? (int)Math.min(source.length(), position) : offset(source, (int)line, (int)column);
        String key = isUnsupported(source, offset) ? "cnpcplus.script.unsupported_syntax"
                : message.toLowerCase(java.util.Locale.ROOT).contains("function") ? "cnpcplus.script.invalid_function"
                : "cnpcplus.script.syntax";
        ScriptDiagnostic.Severity severity = kind.equalsIgnoreCase("WARNING")
                ? ScriptDiagnostic.Severity.WARN : ScriptDiagnostic.Severity.ERROR;
        return make(source, offset, key, message, severity);
    }
    private static void addThrown(Throwable error, String source, List<ScriptDiagnostic> diagnostics) {
        int line = (int)number(error, "getLineNumber", 0);
        int column = (int)number(error, "getColumnNumber", 0);
        int position = (int)number(error, "getPosition", -1);
        String message = error == null ? "Syntax error" : String.valueOf(error.getMessage());
        int offset = position >= 0 ? Math.min(source.length(), position) : offset(source, line, column);
        String key = isUnsupported(source, offset) ? "cnpcplus.script.unsupported_syntax"
                : message.toLowerCase(java.util.Locale.ROOT).contains("function") ? "cnpcplus.script.invalid_function"
                : "cnpcplus.script.syntax";
        ScriptDiagnostic result = make(source, offset, key, message, ScriptDiagnostic.Severity.ERROR);
        if (diagnostics.stream().noneMatch(existing -> existing.line == result.line && existing.column == result.column
                && existing.hintKey.equals(result.hintKey))) diagnostics.add(result);
    }
    private static Object invoke(Object object, String name) {
        if (object == null) return null;
        for (Class<?> type : interfaces(object.getClass())) for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                try { return method.invoke(object); } catch (ReflectiveOperationException ignored) { return null; }
            }
        }
        return null;
    }
    private static long number(Object object, String method, long fallback) {
        Object value = invoke(object, method);
        return value instanceof Number number ? number.longValue() : fallback;
    }
    private static int offset(String source, int line, int column) {
        if (line < 1) return 0;
        int start = 0;
        for (int current = 1; current < line; current++) {
            int newline = source.indexOf('\n', start);
            if (newline < 0) return source.length();
            start = newline + 1;
        }
        return Math.min(source.length(), start + Math.max(0, column - 1));
    }
    private static boolean isUnsupported(String source, int offset) {
        int lineStart = source.lastIndexOf('\n', Math.max(0, Math.min(source.length(), offset)) - 1) + 1;
        int lineEnd = source.indexOf('\n', Math.max(0, Math.min(source.length(), offset)));
        String line = ScriptLexical.mask(source).substring(lineStart, lineEnd < 0 ? source.length() : lineEnd);
        return line.matches("(?s).*(\\?\\.|\\?\\?|\\?\\?=|&&=|\\|\\|=|=>|\\.\\.\\.|`|\\bfunction\\b\\s*[^\\s(]*\\([^)]*=|\\b(?:let|const)\\s+[$_\\p{L}]|\\b(?:var|let|const)\\s+[\\[{]|\\b(?:class|import|export|async|await)\\b|\\d[\\d_]*n\\b|\\d_\\d).*");
    }
    private static ScriptDiagnostic make(String source, int offset, String key, String message, ScriptDiagnostic.Severity severity) {
        int bounded = Math.max(0, Math.min(source.length(), offset));
        int line = 1, lineStart = 0;
        for (int i = 0; i < bounded; i++) if (source.charAt(i) == '\n') { line++; lineStart = i + 1; }
        int length = bounded < source.length() && !Character.isWhitespace(source.charAt(bounded)) ? 1 : 0;
        return new ScriptDiagnostic(line, bounded - lineStart + 1, ScriptDiagnostic.hash(source), message,
                key, message, length, severity);
    }
}
