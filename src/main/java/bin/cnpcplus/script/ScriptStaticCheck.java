package bin.cnpcplus.script;

import java.util.*;
import java.util.regex.*;

/** Conservative lexical lint, not a JS validator. Never evaluates scripts or events. */
public final class ScriptStaticCheck {
    private static final String ID = "[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*";
    private static final Pattern TOKEN = Pattern.compile(ID + "|[0-9][\\w.]*|=>|\\?\\.|\\.\\.\\.|===|!==|==|!=|<=|>=|\\+\\+|--|[^\\s]");
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList("true", "false", "null", "return", "break", "continue", "debugger", "else", "try", "catch", "finally", "do", "throw", "yield", "this", "super", "var", "let", "const", "function", "class", "if", "while", "for", "switch", "case", "default", "new", "delete", "typeof", "void", "instanceof", "in", "of", "with", "async", "await", "extends", "static", "get", "set", "import", "export"));
    private static final Set<String> GLOBALS = new HashSet<>(Arrays.asList("undefined", "NaN", "Infinity", "arguments", "Object", "Array", "String", "Number", "Boolean", "Math", "Date", "JSON", "RegExp", "Error", "TypeError", "ReferenceError", "SyntaxError", "RangeError", "EvalError", "URIError", "Map", "Set", "WeakMap", "WeakSet", "Promise", "Symbol", "ArrayBuffer", "DataView", "Int8Array", "Uint8Array", "Uint8ClampedArray", "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array", "Float64Array", "Java", "JavaImporter", "Packages", "java", "javax", "org", "com", "net", "print", "load", "loadWithNewGlobal", "exit", "quit", "readFully", "readLine", "eval", "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURI", "decodeURI", "encodeURIComponent", "decodeURIComponent", "log", "dump", "PosZero", "AnimationType", "EntityType", "RoleType", "JobType", "SideType", "TacticalType", "PotionType", "ParticleType", "importClass", "importPackage"));
    private static final Set<String> DYNAMIC = new HashSet<>(Arrays.asList("eval", "load", "loadWithNewGlobal", "with", "importClass", "importPackage"));
    private ScriptStaticCheck() { }
    public static ScriptDiagnostic check(String script, Set<String> globals, boolean references) {
        if (script.trim().isEmpty()) return null;
        String code = ScriptApiCompletion.mask(script); List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(code); List<Integer> stack = new ArrayList<>();
        while (matcher.find()) {
            Token token = new Token(matcher.group(), matcher.start()); int index = tokens.size(); tokens.add(token);
            if (token.is("(", "[", "{")) stack.add(index);
            if (token.is(")", "]", "}")) {
                if (stack.isEmpty()) return hint(script, token, "unexpected_bracket");
                int open = stack.remove(stack.size() - 1);
                if (!(tokens.get(open).text + token.text).matches("\\(\\)|\\[\\]|\\{\\}")) return hint(script, tokens.get(open), "unclosed_bracket");
                tokens.get(open).pair = index; token.pair = open;
            }
        }
        if (!stack.isEmpty()) return hint(script, tokens.get(stack.get(stack.size() - 1)), "unclosed_bracket");
        if (!references) return null;
        for (int i = 0; i < tokens.size(); i++) if (DYNAMIC.contains(tokens.get(i).text) && at(tokens, i + 1).equals("(")) return null;
        Bindings bindings = new Bindings(tokens, globals); bindings.collect();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!token.identifier() || token.binding || KEYWORDS.contains(token.text)) continue;
            String before = at(tokens, i - 1), after = at(tokens, i + 1);
            if (before.equals(".") || before.equals("?.")) continue;
            if (after.equals(":") && (before.equals("{") || before.equals(",") || before.equals(";") || i == 0)) continue;
            if (before.equals("break") || before.equals("continue")) continue;
            if (!bindings.declared(token.text, i)) return hint(script, token, "undeclared");
        }
        return null;
    }
    private static ScriptDiagnostic hint(String script, Token token, String kind) {
        int line = 1, lineStart = 0;
        for (int i = 0; i < token.offset; i++) if (script.charAt(i) == '\n') { line++; lineStart = i + 1; }
        return new ScriptDiagnostic(line, token.offset - lineStart + 1, ScriptDiagnostic.hash(script),
                "Static check: possible " + kind + " '" + token.text + "'. No script was executed.", "cnpcplus.script." + kind, token.text, token.text.length());
    }
    private static String at(List<Token> tokens, int i) { return i < 0 || i >= tokens.size() ? "" : tokens.get(i).text; }
    private static final class Token {
        final String text; final int offset; int pair = -1; boolean binding;
        Token(String text, int offset) { this.text = text; this.offset = offset; }
        boolean identifier() { return Character.isJavaIdentifierStart(text.charAt(0)); }
        boolean is(String... choices) { return Arrays.asList(choices).contains(text); }
    }
    private static final class Scope {
        int start; final int end; boolean function; final Set<String> names = new HashSet<>();
        Scope(int start, int end) { this.start = start; this.end = end; }
        boolean contains(int i) { return i >= start && i <= end; }
    }
    private static final class Bindings {
        final List<Token> tokens; final List<Scope> scopes = new ArrayList<>(); final Scope global;
        Bindings(List<Token> tokens, Set<String> globals) {
            this.tokens = tokens; global = new Scope(0, tokens.size()); global.function = true;
            global.names.addAll(GLOBALS); global.names.addAll(globals); scopes.add(global);
            for (int i = 0; i < tokens.size(); i++) if (at(tokens, i).equals("{")) scopes.add(new Scope(i, tokens.get(i).pair));
        }
        // ponytail: lexical scope scans match the brother's lint; this is not a complete JS parser.
        Scope scope(int i, boolean functionOnly) {
            Scope result = global;
            for (Scope scope : scopes) if ((!functionOnly || scope.function) && scope.contains(i) && scope.end - scope.start <= result.end - result.start) result = scope;
            return result;
        }
        boolean declared(String name, int i) {
            for (Scope scope : scopes) if (scope.contains(i) && scope.names.contains(name)) return true;
            return false;
        }
        void bind(int i, Scope scope) {
            if (i < 0 || i >= tokens.size() || !tokens.get(i).identifier()) return;
            tokens.get(i).binding = true; scope.names.add(at(tokens, i));
        }
        void collect() {
            for (int i = 0; i < tokens.size(); i++) {
                String text = at(tokens, i);
                if (text.equals("function") || text.equals("catch")) {
                    int open = i + 1; if (at(tokens, open).equals("*")) open++;
                    int name = -1; if (!at(tokens, open).equals("(")) name = open++;
                    if (!at(tokens, open).equals("(")) continue;
                    int close = tokens.get(open).pair; if (!at(tokens, close + 1).equals("{")) continue;
                    Scope body = scope(close + 1, false); body.start = open; body.function = text.equals("function");
                    if (name >= 0) bind(name, Arrays.asList("=", "(", ",", ":", "return").contains(at(tokens, i - 1)) ? body : scope(i, false));
                    patterns(open + 1, close, body);
                } else if (text.equals("=>")) {
                    int end = expressionEnd(i + 1, tokens.size()); Scope body;
                    if (at(tokens, i + 1).equals("{")) body = scope(i + 1, false);
                    else { body = new Scope(i + 1, end - 1); scopes.add(body); }
                    body.function = true;
                    if (at(tokens, i - 1).equals(")")) { body.start = tokens.get(i - 1).pair; patterns(body.start + 1, i - 1, body); }
                    else { body.start = i - 1; bind(i - 1, body); }
                } else if (text.equals("class")) bind(i + 1, scope(i, false));
                else if (tokens.get(i).identifier() && at(tokens, i + 1).equals("(") && !KEYWORDS.contains(text)) {
                    int close = tokens.get(i + 1).pair;
                    if (at(tokens, close + 1).equals("{") && Arrays.asList("{", ",", "}", "get", "set", "static", "async").contains(at(tokens, i - 1))) {
                        tokens.get(i).binding = true; Scope body = scope(close + 1, false);
                        body.start = i + 1; body.function = true; patterns(i + 2, close, body);
                    }
                }
            }
            for (int i = 0; i < tokens.size(); i++) {
                if (!at(tokens, i).equals("for") || !at(tokens, i + 1).equals("(")) continue;
                int close = tokens.get(i + 1).pair;
                int end = at(tokens, close + 1).equals("{") ? tokens.get(close + 1).pair : expressionEnd(close + 1, tokens.size());
                scopes.add(new Scope(i, end));
            }
            for (int i = 0; i < tokens.size(); i++) {
                if (!tokens.get(i).is("var", "let", "const")) continue;
                Scope target = scope(i, at(tokens, i).equals("var")); int end = i + 1;
                while (end < tokens.size() && !tokens.get(end).is(";", ")", "in", "of")) {
                    if (tokens.get(end).is("(", "[", "{")) end = tokens.get(end).pair;
                    end++;
                }
                patterns(i + 1, end, target);
            }
        }
        int expressionEnd(int start, int end) {
            while (start < end && !tokens.get(start).is(",", ";", ")", "]", "}", "in", "of")) {
                if (tokens.get(start).is("(", "[", "{")) start = tokens.get(start).pair;
                start++;
            }
            return start;
        }
        void patterns(int start, int end, Scope scope) {
            while (start < end) {
                int next = pattern(start, end, scope); if (next <= start) return;
                start = next;
                if (at(tokens, start).equals("=")) start = expressionEnd(start + 1, end);
                if (!at(tokens, start).equals(",")) return;
                start++;
            }
        }
        int pattern(int start, int end, Scope scope) {
            if (start >= end) return start;
            if (at(tokens, start).equals("...")) return pattern(start + 1, end, scope);
            Token token = tokens.get(start);
            if (token.identifier()) { bind(start, scope); return start + 1; }
            if (!token.is("{", "[")) return start;
            int close = token.pair; boolean object = token.text.equals("{"); int i = start + 1;
            while (i < close) {
                if (at(tokens, i).equals(",")) { i++; continue; }
                if (object && at(tokens, i).equals("[")) { i = tokens.get(i).pair + 1; if (at(tokens, i).equals(":")) i++; }
                else if (object && at(tokens, i + 1).equals(":")) { tokens.get(i).binding = true; i += 2; }
                int next = pattern(i, close, scope); if (next <= i) return close + 1;
                i = next; if (at(tokens, i).equals("=")) i = expressionEnd(i + 1, close);
            }
            return close + 1;
        }
    }
}
