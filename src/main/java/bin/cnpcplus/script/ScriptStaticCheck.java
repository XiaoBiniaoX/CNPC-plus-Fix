package bin.cnpcplus.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import top.cnpcplus.script.tools.ScriptApiRegistry;

/** Conservative static script checks. Never evaluates scripts or events. */
public final class ScriptStaticCheck {
    public record Result(ScriptError error, int start, int end, String key, String token) {}
    private static final String ID = "[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*";
    private static final Pattern TOKEN = Pattern.compile(ID + "|[0-9][\\w.]*|\\?\\?=|&&=|\\|\\|=|=>|\\?\\?|\\?\\.|\\.\\.\\.|===|!==|==|!=|<=|>=|\\+\\+|--|[^\\s]");
    private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "return", "break", "continue",
            "debugger", "else", "try", "catch", "finally", "do", "throw", "yield", "this", "super", "var",
            "let", "const", "function", "class", "if", "while", "for", "switch", "case", "default", "new",
            "delete", "typeof", "void", "instanceof", "in", "of", "with", "async", "await", "extends",
            "static", "get", "set", "import", "export");
    private static final Set<String> GLOBALS = Set.of("undefined", "NaN", "Infinity", "arguments", "Object",
            "Array", "String", "Number", "Boolean", "Math", "Date", "JSON", "RegExp", "Error", "TypeError",
            "ReferenceError", "SyntaxError", "RangeError", "EvalError", "URIError", "Map", "Set", "WeakMap",
            "WeakSet", "Promise", "Symbol", "ArrayBuffer", "DataView", "Int8Array", "Uint8Array",
            "Uint8ClampedArray", "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array",
            "Float64Array", "Java", "JavaImporter", "Packages", "java", "javax", "org", "com", "net",
            "print", "load", "loadWithNewGlobal", "exit", "quit", "readFully", "readLine", "eval",
            "parseInt", "parseFloat", "isNaN", "isFinite", "encodeURI", "decodeURI", "encodeURIComponent",
            "decodeURIComponent", "log", "dump", "PosZero", "AnimationType", "EntitiesType", "RoleType",
            "JobType", "SideType", "PotionEffectType", "ParticleType", "importClass", "importPackage",
            "API", "NpcAPI", "npc", "player", "world", "block", "item", "event", "script", "target");
    private static final Set<String> DYNAMIC = Set.of("eval", "load", "loadWithNewGlobal", "with", "importClass", "importPackage");
    private ScriptStaticCheck() {}

    /** Compatibility entry point for callers that only display the first actionable finding. */
    public static Result check(String script, Set<String> additionalGlobals, boolean references) {
        ScriptDiagnostic finding = checkAll(script, additionalGlobals, references, Set.of()).stream()
                .filter(item -> item.severity != ScriptDiagnostic.Severity.INFO).findFirst().orElse(null);
        if (finding == null) return null;
        int start = offset(script, finding.line, finding.column);
        return new Result(new ScriptError(finding.line, ScriptError.hash(script), finding.message),
                start, Math.min(script.length(), start + finding.length), finding.hintKey, finding.subject);
    }

    public static List<ScriptDiagnostic> checkAll(String script, Set<String> additionalGlobals,
                                                   boolean references, Set<String> hooks) {
        if (script == null || script.isBlank()) return List.of();
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(ScriptLexical.mask(script));
        List<Integer> stack = new ArrayList<>();
        List<ScriptDiagnostic> findings = new ArrayList<>();
        boolean balanced = true;
        while (matcher.find()) {
            Token token = new Token(matcher.group(), matcher.start());
            int index = tokens.size();
            tokens.add(token);
            if (token.is("(", "[", "{")) stack.add(index);
            if (token.is(")", "]", "}")) {
                if (stack.isEmpty()) {
                    findings.add(hint(script, token.offset, token.text.length(), "unexpected_bracket", token.text,
                            ScriptDiagnostic.Severity.ERROR));
                    balanced = false;
                    continue;
                }
                int open = stack.removeLast();
                if (!(tokens.get(open).text + token.text).matches("\\(\\)|\\[\\]|\\{\\}")) {
                    Token opening = tokens.get(open);
                    findings.add(hint(script, opening.offset, opening.text.length(), "unclosed_bracket", opening.text,
                            ScriptDiagnostic.Severity.ERROR));
                    balanced = false;
                    continue;
                }
                tokens.get(open).pair = index;
                token.pair = open;
            }
        }
        for (int open : stack) {
            Token token = tokens.get(open);
            findings.add(hint(script, token.offset, token.text.length(), "unclosed_bracket", token.text,
                    ScriptDiagnostic.Severity.ERROR));
            balanced = false;
        }
        ScriptNashornAnalyzer.Result parsed = ScriptNashornAnalyzer.parse(script);
        for (ScriptLexical.Issue issue : ScriptLexical.issues(script)) {
            if (issue.kind().equals("template_literal")) {
                if (!parsed.available()) findings.add(hint(script, issue.offset(), issue.length(),
                        "unsupported_syntax", "template literal", ScriptDiagnostic.Severity.ERROR));
            } else findings.add(hint(script, issue.offset(), issue.length(), issue.kind(), issue.subject(),
                    ScriptDiagnostic.Severity.ERROR));
        }
        findings.addAll(parsed.diagnostics());
        boolean dynamic = false;
        if (references && balanced) for (int i = 0; i < tokens.size(); i++) {
            if (DYNAMIC.contains(tokens.get(i).text) && at(tokens, i + 1).equals("(")) { dynamic = true; break; }
        }
        boolean referencesEnabled = references && balanced && !dynamic;
        if (referencesEnabled) {
        Bindings bindings = new Bindings(tokens, additionalGlobals == null ? Set.of() : additionalGlobals);
        bindings.collect();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!token.identifier() || token.binding || KEYWORDS.contains(token.text)) continue;
            String before = at(tokens, i - 1), after = at(tokens, i + 1);
            if (before.equals(".") || before.equals("?.")) continue;
            if (ScriptApiRegistry.roots().containsKey(token.text) && after.equals(".")) {
                StringBuilder path = new StringBuilder(token.text);
                int end = i + 1;
                while (at(tokens, end).equals(".") && end + 1 < tokens.size() && tokens.get(end + 1).identifier()) {
                    path.append('.').append(tokens.get(end + 1).text);
                    end += 2;
                }
                if (at(tokens, end).equals("(") && ScriptApiRegistry.isClientOnly(path.toString()))
                    findings.add(hint(script, token.offset, token.text.length(), "client_only", path.toString(),
                            ScriptDiagnostic.Severity.ERROR));
            }
            if (after.equals(":") && (before.equals("{") || before.equals(",") || before.equals(";") || i == 0)) continue;
            if (before.equals("break") || before.equals("continue")) continue;
            if (!bindings.declared(token.text, i)) findings.add(hint(script, token.offset, token.text.length(),
                    "undeclared", token.text, ScriptDiagnostic.Severity.WARN));
        }
        }
        if (parsed.tree() != null)
            analyzeTree(script, parsed.tree(), tokens, referencesEnabled, hooks == null ? Set.of() : hooks, findings);
        if (!parsed.available() || parsed.tree() == null)
            analyzeTokens(script, tokens, referencesEnabled, additionalGlobals == null ? Set.of() : additionalGlobals,
                    hooks == null ? Set.of() : hooks, findings);
        findings.sort(Comparator.comparingInt((ScriptDiagnostic item) -> item.severity.ordinal())
                .thenComparingInt(item -> offset(script, item.line, item.column)));
        Map<String, ScriptDiagnostic> unique = new java.util.LinkedHashMap<>();
        for (ScriptDiagnostic finding : findings) unique.putIfAbsent(finding.severity + ":" + finding.line + ":"
                + finding.column + ":" + finding.hintKey, finding);
        return List.copyOf(unique.values());
    }

    private static ScriptDiagnostic hint(String script, int offset, int length, String kind, String subject,
                                         ScriptDiagnostic.Severity severity) {
        int bounded = Math.max(0, Math.min(script.length(), offset));
        int line = 1, lineStart = 0;
        for (int i = 0; i < bounded; i++) if (script.charAt(i) == '\n') { line++; lineStart = i + 1; }
        return new ScriptDiagnostic(line, bounded - lineStart + 1, ScriptDiagnostic.hash(script),
                "Static check: " + kind + " '" + subject + "'. No script was executed.",
                "cnpcplus.script." + kind, subject, Math.max(0, length), severity);
    }

    public static int offset(String source, int line, int column) {
        int start = 0;
        for (int current = 1; current < line; current++) {
            int next = source.indexOf('\n', start);
            if (next < 0) return source.length();
            start = next + 1;
        }
        return Math.min(source.length(), start + Math.max(0, column - 1));
    }

    private static void analyzeTree(String script, Object tree, List<Token> tokens, boolean references,
                                    Set<String> hooks, List<ScriptDiagnostic> findings) {
        List<Object> nodes = ScriptNashornAnalyzer.nodes(tree);
        Map<String, Integer> referencesByName = new HashMap<>();
        if (references) for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.identifier() && !token.binding && !KEYWORDS.contains(token.text)
                    && !at(tokens, i - 1).equals(".") && !at(tokens, i - 1).equals("?."))
                referencesByName.merge(token.text, 1, Integer::sum);
        }
        for (Object node : nodes) {
            int start = ScriptNashornAnalyzer.start(node);
            int end = ScriptNashornAnalyzer.end(node, script.length());
            if (ScriptNashornAnalyzer.is(node, "BlockTree")) {
                List<?> statements = ScriptNashornAnalyzer.list(node, "getStatements");
                if (statements.isEmpty()) findings.add(hint(script, start, Math.min(1, script.length() - start),
                        "empty_block", "{}", ScriptDiagnostic.Severity.WARN));
                boolean stopped = false;
                for (Object statement : statements) {
                    if (stopped) findings.add(hint(script, ScriptNashornAnalyzer.start(statement),
                            Math.max(1, ScriptNashornAnalyzer.end(statement, script.length()) - ScriptNashornAnalyzer.start(statement)),
                            "unreachable", "", ScriptDiagnostic.Severity.INFO));
                    if (ScriptNashornAnalyzer.is(statement, "ReturnTree") || ScriptNashornAnalyzer.is(statement, "ThrowTree")) stopped = true;
                }
            }
            if (ScriptNashornAnalyzer.is(node, "FunctionDeclarationTree") && references) {
                String name = ScriptNashornAnalyzer.identifier(node);
                if (!name.isEmpty() && !hooks.contains(name) && referencesByName.getOrDefault(name, 0) == 0)
                    findings.add(hint(script, ScriptNashornAnalyzer.start(ScriptNashornAnalyzer.get(node, "getName")),
                            name.length(), "unused_function", name, ScriptDiagnostic.Severity.INFO));
            }
            if (ScriptNashornAnalyzer.is(node, "VariableTree") && references) {
                Object binding = ScriptNashornAnalyzer.get(node, "getBinding");
                if (ScriptNashornAnalyzer.is(binding, "IdentifierTree")) {
                    String name = ScriptNashornAnalyzer.identifier(binding);
                    if (!name.isEmpty() && referencesByName.getOrDefault(name, 0) == 0)
                        findings.add(hint(script, ScriptNashornAnalyzer.start(binding), name.length(),
                                "unused_variable", name, ScriptDiagnostic.Severity.INFO));
                }
            }
            if (ScriptNashornAnalyzer.is(node, "IfTree")) {
                Object condition = ScriptNashornAnalyzer.get(node, "getCondition");
                Object otherwise = ScriptNashornAnalyzer.get(node, "getElseStatement");
                if (ScriptNashornAnalyzer.is(otherwise, "IfTree")) {
                    String current = source(script, condition).trim();
                    String next = source(script, ScriptNashornAnalyzer.get(otherwise, "getCondition")).trim();
                    if (!current.isEmpty() && current.equals(next)) findings.add(hint(script,
                            ScriptNashornAnalyzer.start(otherwise), 2, "duplicate_condition", current, ScriptDiagnostic.Severity.WARN));
                }
            }
            if (ScriptNashornAnalyzer.is(node, "SwitchTree")) {
                Map<String, Object> cases = new HashMap<>();
                for (Object branch : ScriptNashornAnalyzer.list(node, "getCases")) {
                    Object expression = ScriptNashornAnalyzer.get(branch, "getExpression");
                    if (expression == null) continue;
                    String value = source(script, expression).trim();
                    if (cases.putIfAbsent(value, branch) != null) findings.add(hint(script, ScriptNashornAnalyzer.start(branch),
                            Math.max(1, ScriptNashornAnalyzer.end(branch, script.length()) - ScriptNashornAnalyzer.start(branch)),
                            "duplicate_case", value, ScriptDiagnostic.Severity.WARN));
                }
            }
            if (isConstantLoop(node, script) && !terminatesLoopBody(ScriptNashornAnalyzer.get(node, "getStatement")))
                findings.add(hint(script, start, Math.max(1, end - start), "possible_infinite_loop", "loop", ScriptDiagnostic.Severity.WARN));
        }
    }

    private static String source(String script, Object node) {
        if (node == null) return "";
        int start = ScriptNashornAnalyzer.start(node), end = ScriptNashornAnalyzer.end(node, script.length());
        return start >= end ? "" : script.substring(start, end);
    }
    private static boolean isConstantLoop(Object node, String script) {
        if (!ScriptNashornAnalyzer.is(node, "WhileLoopTree") && !ScriptNashornAnalyzer.is(node, "DoWhileLoopTree")
                && !ScriptNashornAnalyzer.is(node, "ForLoopTree")) return false;
        Object condition = ScriptNashornAnalyzer.get(node, "getCondition");
        return condition == null || source(script, condition).replaceAll("[()\\s]", "").equals("true");
    }
    private static boolean terminatesLoopBody(Object body) {
        if (body == null) return false;
        for (Object child : ScriptNashornAnalyzer.nodes(body)) {
            if (ScriptNashornAnalyzer.is(child, "BreakTree") || ScriptNashornAnalyzer.is(child, "ReturnTree")
                    || ScriptNashornAnalyzer.is(child, "ThrowTree")) return true;
        }
        return false;
    }

    private static void analyzeTokens(String script, List<Token> tokens, boolean references,
                                      Set<String> globals, Set<String> hooks, List<ScriptDiagnostic> findings) {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            String previous = at(tokens, i - 1), next = at(tokens, i + 1);
            if (token.is("?.", "??", "??=", "&&=", "||=", "=>", "..."))
                findings.add(hint(script, token.offset, token.text.length(), "unsupported_syntax", token.text, ScriptDiagnostic.Severity.ERROR));
            if (token.is("class", "import", "export") && !previous.equals(".") && !next.equals(":"))
                findings.add(hint(script, token.offset, token.text.length(), "unsupported_syntax", token.text, ScriptDiagnostic.Severity.ERROR));
            if (token.is("let", "const") && !previous.equals(".")
                    && (next.equals("[") || !next.isEmpty() && Character.isJavaIdentifierStart(next.charAt(0))))
                findings.add(hint(script, token.offset, token.text.length(), "unsupported_syntax", token.text, ScriptDiagnostic.Severity.ERROR));
            if (token.text.equals("function")) {
                int open = i + 1;
                if (at(tokens, open).equals("*")) open++;
                if (!at(tokens, open).equals("(")) open++;
                if (at(tokens, open).equals("(") && tokens.get(open).pair > open) {
                    int close = tokens.get(open).pair;
                    for (int parameter = open + 1; parameter < close; parameter++) if (at(tokens, parameter).equals("=")) {
                        Token unsupported = tokens.get(parameter);
                        findings.add(hint(script, unsupported.offset, unsupported.text.length(), "unsupported_syntax", "default parameter", ScriptDiagnostic.Severity.ERROR));
                        break;
                    }
                }
            }
            if (token.text.matches("[0-9][0-9A-Za-z_]*") && (token.text.endsWith("n") || token.text.matches(".*\\d_\\d.*")))
                findings.add(hint(script, token.offset, token.text.length(), "unsupported_syntax", token.text, ScriptDiagnostic.Severity.ERROR));
            if (token.text.equals("{") && token.pair == i + 1
                    && (previous.equals(")") || Set.of("else", "try", "finally", "do").contains(previous)))
                findings.add(hint(script, token.offset, 1, "empty_block", "{}", ScriptDiagnostic.Severity.WARN));
            if (token.text.equals("return") || token.text.equals("throw")) {
                int end = i + 1;
                while (end < tokens.size() && !tokens.get(end).is(";", "}")) {
                    if (tokens.get(end).pair > end) end = tokens.get(end).pair;
                    end++;
                }
                if (at(tokens, end).equals(";") && end + 1 < tokens.size() && !at(tokens, end + 1).equals("}")) {
                    Token unreachable = tokens.get(end + 1);
                    findings.add(hint(script, unreachable.offset, Math.max(1, unreachable.text.length()), "unreachable", "", ScriptDiagnostic.Severity.INFO));
                }
            }
            if (token.text.equals("while") && next.equals("(")) {
                int close = tokens.get(i + 1).pair;
                if (close > i + 2 && close < tokens.size() && at(tokens, i + 2).equals("true") && at(tokens, close + 1).equals("{")) {
                    int body = close + 1, bodyEnd = tokens.get(body).pair;
                    if (bodyEnd > body && bodyEnd < tokens.size() && !hasLoopExit(tokens, body, bodyEnd))
                        findings.add(hint(script, token.offset, tokens.get(bodyEnd).offset + 1 - token.offset,
                                "possible_infinite_loop", "while(true)", ScriptDiagnostic.Severity.WARN));
                }
            }
            if (token.text.equals("for") && next.equals("(")) {
                int close = tokens.get(i + 1).pair;
                if (close > i + 1 && close < tokens.size() && at(tokens, close + 1).equals("{")) {
                    int firstSemi = -1;
                    for (int j = i + 2; j < close; j++) if (at(tokens, j).equals(";")) { firstSemi = j; break; }
                    int body = close + 1, bodyEnd = tokens.get(body).pair;
                    if (firstSemi >= 0 && at(tokens, firstSemi + 1).equals(";") && bodyEnd > body && bodyEnd < tokens.size()
                            && !hasLoopExit(tokens, body, bodyEnd))
                        findings.add(hint(script, token.offset, 3, "possible_infinite_loop", "for(;;)", ScriptDiagnostic.Severity.WARN));
                }
            }
            if (token.text.equals("if") && next.equals("(")) {
                int close = tokens.get(i + 1).pair;
                if (close <= i + 1 || close >= tokens.size()) continue;
                int elseIf = close + 1;
                if (at(tokens, elseIf).equals("{")) {
                    int blockEnd = tokens.get(elseIf).pair;
                    if (blockEnd <= elseIf) continue;
                    elseIf = blockEnd + 1;
                }
                if (at(tokens, elseIf).equals("else") && at(tokens, elseIf + 1).equals("if") && at(tokens, elseIf + 2).equals("(")) {
                    int duplicateClose = tokens.get(elseIf + 2).pair;
                    if (sameSource(script, tokens, i + 2, close, elseIf + 3, duplicateClose))
                        findings.add(hint(script, tokens.get(elseIf + 1).offset, 2, "duplicate_condition", "if", ScriptDiagnostic.Severity.WARN));
                }
            }
            if (token.text.equals("switch") && next.equals("(")) {
                int close = tokens.get(i + 1).pair;
                if (close > i + 1 && close < tokens.size() && at(tokens, close + 1).equals("{")) {
                    int body = close + 1, bodyEnd = tokens.get(body).pair;
                    if (bodyEnd <= body || bodyEnd >= tokens.size()) continue;
                    Set<String> cases = new HashSet<>();
                    for (int j = close + 2; j < bodyEnd; j++) {
                        if (!at(tokens, j).equals("case")) continue;
                        int end = j + 1;
                        while (end < bodyEnd && !at(tokens, end).equals(":")) {
                            if (tokens.get(end).pair > end) end = tokens.get(end).pair;
                            end++;
                        }
                        String value = script.substring(tokens.get(j).offset + 4, tokens.get(Math.min(end, bodyEnd)).offset).trim();
                        if (!cases.add(value)) findings.add(hint(script, tokens.get(j).offset,
                                Math.max(1, end < tokens.size() ? tokens.get(end).offset - tokens.get(j).offset : 4),
                                "duplicate_case", value, ScriptDiagnostic.Severity.WARN));
                        j = end;
                    }
                }
            }
        }
        if (!references) return;
        Map<String, Integer> reads = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!token.identifier() || token.binding || KEYWORDS.contains(token.text)
                    || at(tokens, i - 1).equals(".") || at(tokens, i - 1).equals("?.")) continue;
            if (at(tokens, i + 1).equals(":") || at(tokens, i - 1).equals("break") || at(tokens, i - 1).equals("continue")) continue;
            reads.merge(token.text, 1, Integer::sum);
        }
        for (int i = 0; i < tokens.size(); i++) {
            if (at(tokens, i).equals("function") && tokens.get(Math.min(i + 1, tokens.size() - 1)).identifier()) {
                String name = at(tokens, i + 1);
                if (!hooks.contains(name) && reads.getOrDefault(name, 0) == 0)
                    findings.add(hint(script, tokens.get(i + 1).offset, name.length(), "unused_function", name, ScriptDiagnostic.Severity.INFO));
            }
            if (!tokens.get(i).is("var", "let", "const")) continue;
            int end = i + 1;
            while (end < tokens.size() && !tokens.get(end).is(";", ")", "in", "of")) {
                Token candidate = tokens.get(end);
                if (candidate.binding && candidate.identifier() && reads.getOrDefault(candidate.text, 0) == 0)
                    findings.add(hint(script, candidate.offset, candidate.text.length(), "unused_variable", candidate.text, ScriptDiagnostic.Severity.INFO));
                if (candidate.is("(", "[", "{")) end = Math.max(end, candidate.pair);
                end++;
            }
        }
    }
    private static boolean hasLoopExit(List<Token> tokens, int start, int end) {
        for (int i = start + 1; i < end; i++) if (tokens.get(i).is("break", "return", "throw")) return true;
        return false;
    }
    private static boolean sameSource(String script, List<Token> tokens, int firstStart, int firstEnd, int secondStart, int secondEnd) {
        if (firstStart >= firstEnd || secondStart >= secondEnd) return false;
        return script.substring(tokens.get(firstStart).offset, tokens.get(firstEnd).offset).trim()
                .equals(script.substring(tokens.get(secondStart).offset, tokens.get(secondEnd).offset).trim());
    }
    private static String at(List<Token> tokens, int i) { return i < 0 || i >= tokens.size() ? "" : tokens.get(i).text; }
    private static final class Token {
        final String text;
        final int offset;
        int pair = -1;
        boolean binding;
        Token(String text, int offset) { this.text = text; this.offset = offset; }
        boolean identifier() { return Character.isJavaIdentifierStart(text.charAt(0)); }
        boolean is(String... choices) { return List.of(choices).contains(text); }
    }
    private static final class Scope {
        int start;
        final int end;
        boolean function;
        final Set<String> names = new HashSet<>();
        Scope(int start, int end) { this.start = start; this.end = end; }
        boolean contains(int i) { return i >= start && i <= end; }
    }
    private static final class Bindings {
        final List<Token> tokens;
        final List<Scope> scopes = new ArrayList<>();
        final Scope global;
        Bindings(List<Token> tokens, Set<String> globals) {
            this.tokens = tokens;
            global = new Scope(0, tokens.size()); global.function = true;
            global.names.addAll(GLOBALS); global.names.addAll(globals); scopes.add(global);
            global.names.addAll(ScriptApiRegistry.roots().keySet());
            global.names.addAll(top.cnpcplus.api.script.ScriptCheckApi.registeredGlobals());
            for (int i = 0; i < tokens.size(); i++)
                if (at(tokens, i).equals("{")) scopes.add(new Scope(i, tokens.get(i).pair));
        }
        Scope scope(int i, boolean functionOnly) {
            Scope result = global;
            for (Scope scope : scopes)
                if ((!functionOnly || scope.function) && scope.contains(i) && scope.end - scope.start <= result.end - result.start) result = scope;
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
            // Discover function boundaries first: var is hoisted only into its own function.
            for (int i = 0; i < tokens.size(); i++) {
                String text = at(tokens, i);
                if (text.equals("function") || text.equals("catch")) {
                    int open = i + 1;
                    if (at(tokens, open).equals("*")) open++;
                    int name = -1;
                    if (!at(tokens, open).equals("(")) name = open++;
                    if (!at(tokens, open).equals("(")) continue;
                    int close = tokens.get(open).pair;
                    if (!at(tokens, close + 1).equals("{")) continue;
                    Scope body = scope(close + 1, false); body.start = open; body.function = text.equals("function");
                    if (name >= 0) {
                        boolean expression = Set.of("=", "(", ",", ":", "return").contains(at(tokens, i - 1));
                        bind(name, expression ? body : scope(i, false));
                    }
                    patterns(open + 1, close, body);
                } else if (text.equals("=>")) {
                    int end = expressionEnd(i + 1, tokens.size());
                    Scope body;
                    if (at(tokens, i + 1).equals("{")) body = scope(i + 1, false);
                    else { body = new Scope(i + 1, end - 1); scopes.add(body); }
                    body.function = true;
                    if (at(tokens, i - 1).equals(")")) { body.start = tokens.get(i - 1).pair; patterns(body.start + 1, i - 1, body); }
                    else { body.start = i - 1; bind(i - 1, body); }
                } else if (text.equals("class")) bind(i + 1, scope(i, false));
                else if (tokens.get(i).identifier() && at(tokens, i + 1).equals("(") && !KEYWORDS.contains(text)) {
                    int close = tokens.get(i + 1).pair;
                    if (at(tokens, close + 1).equals("{") && Set.of("{", ",", "}", "get", "set", "static", "async").contains(at(tokens, i - 1))) {
                        tokens.get(i).binding = true;
                        Scope body = scope(close + 1, false); body.start = i + 1; body.function = true;
                        patterns(i + 2, close, body);
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
                Scope target = scope(i, at(tokens, i).equals("var"));
                int end = i + 1;
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
                int next = pattern(start, end, scope);
                if (next <= start) return;
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
            int close = token.pair;
            boolean object = token.text.equals("{");
            int i = start + 1;
            while (i < close) {
                if (at(tokens, i).equals(",")) { i++; continue; }
                if (object && at(tokens, i).equals("[")) { i = tokens.get(i).pair + 1; if (at(tokens, i).equals(":")) i++; }
                else if (object && at(tokens, i + 1).equals(":")) { tokens.get(i).binding = true; i += 2; }
                int next = pattern(i, close, scope);
                if (next <= i) return close + 1;
                i = next;
                if (at(tokens, i).equals("=")) i = expressionEnd(i + 1, close);
            }
            return close + 1;
        }
    }
}
