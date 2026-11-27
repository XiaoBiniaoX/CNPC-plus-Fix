package bin.cnpcplus.script;

import java.util.*;
import java.util.regex.*;
import bin.cnpcplus.api.script.ScriptCheckApi;
import bin.cnpcplus.api.script.ScriptCompletionApi;

/** Conservative lexical lint, not a JS validator. Never evaluates scripts or events. */
public final class ScriptStaticCheck {
    private static final String ID = "[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*";
    private static final Pattern TOKEN = Pattern.compile(ID + "|[0-9][\\w.]*|\\?\\?=|&&=|\\|\\|=|=>|\\?\\?|\\?\\.|\\.\\.\\.|===|!==|==|!=|<=|>=|\\+\\+|--|[^\\s]");
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList("true", "false", "null", "return", "break", "continue", "debugger", "else", "try", "catch", "finally", "do", "throw", "yield", "this", "super", "var", "let", "const", "function", "class", "if", "while", "for", "switch", "case", "default", "new", "delete", "typeof", "void", "instanceof", "in", "of", "with", "async", "await", "extends", "static", "get", "set", "import", "export"));
    private static final Set<String> GLOBALS = new HashSet<>(Arrays.asList("undefined", "NaN", "Infinity", "arguments", "Object", "Array", "String", "Number", "Boolean", "Math", "Date", "JSON", "RegExp", "Error", "TypeError", "ReferenceError", "SyntaxError", "RangeError", "EvalError", "URIError", "Map", "Set", "WeakMap", "WeakSet", "Promise", "Symbol", "ArrayBuffer", "DataView", "Int8Array", "Uint8Array", "Uint8ClampedArray", "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array", "Float64Array", "Java", "JavaImporter", "Packages", "java", "javax", "org", "com", "net", "print", "load", "loadWithNewGlobal", "exit", "quit", "readFully", "readLine", "eval", "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURI", "decodeURI", "encodeURIComponent", "decodeURIComponent", "log", "dump", "PosZero", "AnimationType", "EntityType", "RoleType", "JobType", "SideType", "TacticalType", "PotionType", "ParticleType", "importClass", "importPackage"));
    private static final Set<String> DYNAMIC = new HashSet<>(Arrays.asList("eval", "load", "loadWithNewGlobal", "with", "importClass", "importPackage"));
    private ScriptStaticCheck() { }
    public static ScriptDiagnostic check(String script, Set<String> globals, boolean references) {
        List<ScriptDiagnostic> all = checkAll(script, globals, references);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<ScriptDiagnostic> checkAll(String script, Set<String> globals, boolean references) {
        return checkAll(script, "JavaScript", globals, references);
    }

    public static List<ScriptDiagnostic> checkAll(String script, String language, Set<String> globals, boolean references) {
        return check(script, language, globals, references, true);
    }

    /** ES5 language checker implementation, excluding separately registered addon checkers. */
    public static List<ScriptDiagnostic> checkBuiltinAll(String script, String language, Set<String> globals, boolean references) {
        return check(script, language, globals, references, false, true);
    }

    private static List<ScriptDiagnostic> check(String script, String language, Set<String> globals,
            boolean references, boolean includeAddonCheckers) {
        return check(script, language, globals, references, includeAddonCheckers, false);
    }

    private static List<ScriptDiagnostic> check(String script, String language, Set<String> globals,
            boolean references, boolean includeAddonCheckers, boolean forceBuiltin) {
        if (script == null || script.trim().isEmpty()) return Collections.emptyList();
        List<ScriptDiagnostic> out = new ArrayList<>();
        if (!forceBuiltin && !ScriptCompletionApi.usesBuiltinChecks(language)) {
            if (includeAddonCheckers) addonChecks(script, language, globals, out);
            return out;
        }
        ScriptLexical lexical = ScriptLexical.scan(script);
        for (ScriptLexical.Issue issue : lexical.issues())
            add(out, script, issue.offset, issue.length, ScriptDiagnostic.Severity.ERROR, issue.key, issue.subject);

        String code = lexical.masked();
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(code);
        List<Integer> stack = new ArrayList<>();
        while (matcher.find()) {
            Token token = new Token(matcher.group(), matcher.start()); int index = tokens.size(); tokens.add(token);
            if (token.is("(", "[", "{")) stack.add(index);
            if (!token.is(")", "]", "}")) continue;
            if (stack.isEmpty()) {
                add(out, script, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.unexpected_bracket", token.text);
                continue;
            }
            int open = stack.remove(stack.size() - 1);
            Token opening = tokens.get(open);
            if (!matches(opening.text, token.text)) {
                add(out, script, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.mismatched_bracket", token.text);
                continue;
            }
            opening.pair = index; token.pair = open;
        }
        for (int open : stack) {
            Token token = tokens.get(open);
            add(out, script, token.offset, 1, ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.unclosed_bracket", token.text);
        }

        syntax(script, tokens, out);
        if (references) references(script, tokens, globals, out);
        if (includeAddonCheckers) addonChecks(script, language, globals, out);
        Collections.sort(out, Comparator.comparingInt((ScriptDiagnostic d) -> d.offset < 0 ? Integer.MAX_VALUE : d.offset)
                .thenComparingInt(d -> d.severity.ordinal()));
        return out;
    }

    private static boolean matches(String open, String close) {
        return open.equals("(") && close.equals(")") || open.equals("[") && close.equals("]")
                || open.equals("{") && close.equals("}");
    }

    private static void add(List<ScriptDiagnostic> out, String source, int offset, int length,
            ScriptDiagnostic.Severity severity, String key, String subject) {
        out.add(ScriptDiagnostic.at(source, offset, length, severity, key, subject));
    }

    private static void syntax(String source, List<Token> tokens, List<ScriptDiagnostic> out) {
        Set<String> operators = new HashSet<>(Arrays.asList("=", "+", "-", "*", "/", "%", "==", "===", "!=", "!==", "<", ">", "<=", ">=", "&&", "||", "??", "in", "instanceof", "=>"));
        Map<String, Integer> cases = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i); String next = at(tokens, i + 1);
            if (token.is("?.", "??", "??=", "&&=", "||=", "async", "await", "class"))
                add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.WARN, "cnpcplus.script.unsupported_syntax", token.text);
            if (token.is("if", "while", "for", "switch", "catch") && !next.equals("("))
                add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.missing_structure", token.text + " (...) ");
            if (token.is("if", "while", "for", "switch", "catch") && next.equals("(") && tokens.get(i + 1).pair >= 0) {
                int close = tokens.get(i + 1).pair;
                if (tokens.get(i + 1).pair == i + 2)
                    add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.missing_structure", token.text + " condition");
                else if (at(tokens, close + 1).isEmpty()
                        || Arrays.asList("}", "else", "case", "default").contains(at(tokens, close + 1)))
                    add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.missing_structure", token.text + " body");
            }
            if (token.equalsText("switch") && next.equals("(") && tokens.get(i + 1).pair > i + 1
                    && !at(tokens, tokens.get(i + 1).pair + 1).equals("{"))
                add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.missing_structure", "switch body");
            if (token.equalsText("function")) checkFunction(source, tokens, i, out);
            if (token.is("var", "let", "const") && (next.isEmpty() || next.equals("=") || next.equals(";")))
                add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.invalid_declaration", token.text);
            if (token.equalsText("=") && (next.equals(";") || next.equals("}") || next.isEmpty()))
                add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.invalid_expression", "=");
            if (operators.contains(token.text) && operators.contains(next)
                    && !(next.equals("+") || next.equals("-")))
                add(out, source, token.offset, token.text.length() + next.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.invalid_expression", token.text + next);
            if (token.equalsText("while") && next.equals("(") && i + 1 < tokens.size() && tokens.get(i + 1).pair > i + 1) {
                int close = tokens.get(i + 1).pair;
                String condition = join(tokens, i + 2, close);
                if (condition.equals("true") || condition.equals("1") || condition.equals("1===1") || condition.equals("1==1"))
                    add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.WARN, "cnpcplus.script.possible_infinite_loop", "while");
            }
            if (token.equalsText("for") && next.equals("(") && i + 1 < tokens.size() && tokens.get(i + 1).pair > i + 1) {
                int close = tokens.get(i + 1).pair;
                if (at(tokens, i + 2).equals(";") && at(tokens, i + 3).equals(";") && close == i + 4)
                    add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.WARN, "cnpcplus.script.possible_infinite_loop", "for (;;)");
            }
            if (token.equalsText("return") || token.equalsText("throw")) checkUnreachable(source, tokens, i, out);
            if (token.equalsText("case")) {
                String value = literalValue(source, tokens, i + 1);
                if (value != null) {
                    String switchKey = enclosingSwitch(tokens, i) + ":" + value;
                    Integer previous = cases.put(switchKey, token.offset);
                    if (previous != null)
                        add(out, source, token.offset, tokens.get(i + 1).text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.duplicate_case", value);
                }
            }
            if (token.equalsText("{" ) && token.pair == i + 1 && isBlock(tokens, i))
                add(out, source, token.offset, 1, ScriptDiagnostic.Severity.INFO, "cnpcplus.script.empty_block", "{}");
        }
        duplicateBranches(source, tokens, out);
    }

    private static void checkFunction(String source, List<Token> tokens, int i, List<ScriptDiagnostic> out) {
        int open = i + 1;
        if (at(tokens, open).equals("*")) open++;
        if (tokens.size() > open && tokens.get(open).identifier()) open++;
        if (!at(tokens, open).equals("(")) {
            add(out, source, tokens.get(i).offset, tokens.get(i).text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.invalid_function", "function");
            return;
        }
        int close = tokens.get(open).pair;
        if (close < 0 || !at(tokens, close + 1).equals("{"))
            add(out, source, tokens.get(i).offset, tokens.get(i).text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.missing_structure", "function body");
    }

    private static void checkUnreachable(String source, List<Token> tokens, int i, List<ScriptDiagnostic> out) {
        int end = i + 1;
        while (end < tokens.size() && !tokens.get(end).is(";", "}") && line(source, tokens.get(end).offset) == line(source, tokens.get(i).offset)) end++;
        if (at(tokens, end).equals(";")) end++;
        if (end < tokens.size() && !tokens.get(end).is("}", "else", "case", "default", ";"))
            add(out, source, tokens.get(end).offset, tokens.get(end).text.length(), ScriptDiagnostic.Severity.WARN, "cnpcplus.script.unreachable_code", tokens.get(end).text);
    }

    private static void duplicateBranches(String source, List<Token> tokens, List<ScriptDiagnostic> out) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!at(tokens, i).equals("if") || !at(tokens, i + 1).equals("(")) continue;
            Set<String> seen = new HashSet<>();
            int current = i;
            while (at(tokens, current).equals("if") && at(tokens, current + 1).equals("(")) {
                int close = tokens.get(current + 1).pair;
                if (close < 0) break;
                String condition = source.substring(tokens.get(current + 1).offset, tokens.get(close).offset + 1).replaceAll("\\s+", "");
                if (!seen.add(condition)) add(out, source, tokens.get(current).offset, 2,
                        ScriptDiagnostic.Severity.WARN, "cnpcplus.script.duplicate_branch", "if");
                int body = close + 1;
                if (at(tokens, body).equals("{")) body = tokens.get(body).pair + 1;
                else while (body < tokens.size() && !tokens.get(body).is(";", "}")) body++;
                if (at(tokens, body).equals(";")) body++;
                if (!at(tokens, body).equals("else") || !at(tokens, body + 1).equals("if")) break;
                current = body + 1;
            }
        }
    }

    private static int enclosingSwitch(List<Token> tokens, int index) {
        int found = -1;
        for (int i = 0; i < index; i++) if (at(tokens, i).equals("switch") && at(tokens, i + 1).equals("(")) {
            int close = tokens.get(i + 1).pair;
            if (close >= 0 && at(tokens, close + 1).equals("{") && tokens.get(close + 1).pair >= index) found = i;
        }
        return found;
    }

    private static String literalValue(String source, List<Token> tokens, int index) {
        if (index >= tokens.size()) return null;
        Token token = tokens.get(index);
        char first = source.charAt(token.offset);
        if (first == '\'' || first == '"') {
            int end = token.offset + 1;
            while (end < source.length()) {
                char c = source.charAt(end++);
                if (c == '\\') end++;
                else if (c == first) break;
            }
            return source.substring(token.offset, Math.min(end, source.length()));
        }
        return token.text.matches("-?[0-9]+(?:\\.[0-9]+)?") || token.is("true", "false", "null") ? token.text : null;
    }

    private static boolean isBlock(List<Token> tokens, int i) {
        String previous = at(tokens, i - 1);
        if (previous.equals(")")) return true;
        return Arrays.asList("else", "try", "finally", "do", "=>").contains(previous)
                || i == 0 || Arrays.asList(";", "}").contains(previous);
    }

    private static String join(List<Token> tokens, int start, int end) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) result.append(tokens.get(i).text);
        return result.toString();
    }

    private static int line(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < source.length(); i++) if (source.charAt(i) == '\n') line++;
        return line;
    }

    private static void references(String source, List<Token> tokens, Set<String> globals, List<ScriptDiagnostic> out) {
        Set<String> known = new HashSet<>(globals == null ? Collections.<String>emptySet() : globals);
        known.addAll(ScriptCheckApi.registeredGlobals());
        known.addAll(bin.cnpcplus.script.tools.ScriptApiRegistry.roots().keySet());
        Bindings bindings = new Bindings(tokens, known); bindings.collect();
        boolean dynamic = false;
        for (int i = 0; i < tokens.size(); i++) if (DYNAMIC.contains(tokens.get(i).text) && at(tokens, i + 1).equals("(")) dynamic = true;
        if (dynamic) add(out, source, 0, 0, ScriptDiagnostic.Severity.INFO, "cnpcplus.script.dynamic_scope", "eval/load/with");
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).identifier() || at(tokens, i - 1).equals(".")) continue;
            String name = at(tokens, i); int end = i;
            while (at(tokens, end + 1).equals(".") && end + 2 < tokens.size() && tokens.get(end + 2).identifier()) {
                name += "." + at(tokens, end + 2); end += 2;
            }
            if (at(tokens, end + 1).equals("(") && bin.cnpcplus.script.tools.ScriptApiRegistry.isClientOnly(name))
                add(out, source, tokens.get(i).offset, tokens.get(i).text.length(), ScriptDiagnostic.Severity.ERROR, "cnpcplus.script.client_thread_api", name);
        }
        if (!dynamic) for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!token.identifier() || token.binding || KEYWORDS.contains(token.text)) continue;
            String before = at(tokens, i - 1), after = at(tokens, i + 1);
            if (before.equals(".") || before.equals("?.")) continue;
            if (after.equals(":") && (before.equals("{") || before.equals(",") || before.equals(";") || i == 0)) continue;
            if (before.equals("break") || before.equals("continue")) continue;
            if (!bindings.declared(token.text, i))
                add(out, source, token.offset, token.text.length(), ScriptDiagnostic.Severity.WARN, "cnpcplus.script.undeclared", token.text);
        }
        unusedDeclarations(source, tokens, out);
    }

    private static void unusedDeclarations(String source, List<Token> tokens, List<ScriptDiagnostic> out) {
        Set<String> eventNames = new HashSet<>(Arrays.asList("init", "tick", "interact", "damaged", "died", "attack", "collide", "kill", "timer", "target", "target_lost", "projectile_tick", "projectile_impact"));
        for (int i = 0; i < tokens.size(); i++) {
            int nameIndex = -1;
            if (tokens.get(i).is("var", "let", "const") && tokens.get(i + 1 < tokens.size() ? i + 1 : i).identifier()) nameIndex = i + 1;
            else if (tokens.get(i).equalsText("function") && tokens.size() > i + 1 && tokens.get(i + 1).identifier()) nameIndex = i + 1;
            if (nameIndex < 0) continue;
            Token name = tokens.get(nameIndex);
            if (eventNames.contains(name.text.toLowerCase(Locale.ROOT))) continue;
            int uses = 0;
            for (int j = 0; j < tokens.size(); j++) if (j != nameIndex && tokens.get(j).text.equals(name.text)
                    && tokens.get(j).identifier() && !tokens.get(j).binding && !at(tokens, j - 1).equals(".")) uses++;
            if (uses == 0) add(out, source, name.offset, name.text.length(), ScriptDiagnostic.Severity.INFO,
                    tokens.get(i).equalsText("function") ? "cnpcplus.script.unused_function" : "cnpcplus.script.unused_variable", name.text);
        }
    }

    private static void addonChecks(String source, String language, Set<String> globals, List<ScriptDiagnostic> out) {
        Set<String> known = new HashSet<>(globals == null ? Collections.<String>emptySet() : globals);
        known.addAll(ScriptCheckApi.registeredGlobals());
        known.addAll(bin.cnpcplus.script.tools.ScriptApiRegistry.roots().keySet());
        ScriptCheckApi.Source input = new ScriptCheckApi.Source(source, language, known);
        for (ScriptCheckApi.Finding finding : ScriptCheckApi.check(input)) {
            ScriptDiagnostic.Severity severity = finding.severity() == ScriptCheckApi.Severity.ERROR ? ScriptDiagnostic.Severity.ERROR
                    : finding.severity() == ScriptCheckApi.Severity.INFO ? ScriptDiagnostic.Severity.INFO : ScriptDiagnostic.Severity.WARN;
            add(out, source, Math.min(finding.offset(), source.length()), finding.length(), severity,
                    finding.translationKey(), finding.subject());
        }
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
        boolean equalsText(String value) { return text.equals(value); }
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
            global.names.addAll(bin.cnpcplus.script.tools.ScriptApiRegistry.roots().keySet());
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
