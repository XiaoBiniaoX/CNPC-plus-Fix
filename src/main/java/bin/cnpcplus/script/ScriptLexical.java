package bin.cnpcplus.script;

import java.util.*;

/** Offset-preserving lexical masking; no engine, script execution or API calls. */
final class ScriptLexical {
    private static final List<String> PREFIX = Arrays.asList("return", "throw", "case", "delete", "typeof", "void", "new", "yield", "else", "do", "in", "of", "instanceof", "await");
    private static final List<String> CONTROL = Arrays.asList("if", "while", "for", "switch", "catch", "with");
    private final String text;
    private final char[] out;
    private final List<Issue> issues = new ArrayList<>();
    private int cursor;
    private ScriptLexical(String text) {
        this.text = text; out = new char[text.length()]; Arrays.fill(out, ' ');
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n' || text.charAt(i) == '\r') out[i] = text.charAt(i);
    }
    static final class Issue {
        final int offset, length;
        final String key, subject;
        Issue(int offset, int length, String key, String subject) {
            this.offset = offset; this.length = length; this.key = key; this.subject = subject;
        }
    }
    static ScriptLexical scan(String text) { ScriptLexical lexer = new ScriptLexical(text); lexer.code(false); return lexer; }
    List<Issue> issues() { return issues; }
    String masked() { return new String(out); }
    static String mask(String text) {
        return scan(text).masked();
    }
    private void code(boolean interpolation) {
        boolean regex = true; String previous = ""; int braces = 0;
        Deque<Boolean> controlParens = new ArrayDeque<>(), objectBraces = new ArrayDeque<>();
        while (cursor < text.length()) {
            char c = text.charAt(cursor), next = cursor + 1 < text.length() ? text.charAt(cursor + 1) : 0;
            if (Character.isWhitespace(c)) { cursor++; continue; }
            if (c == '/' && next == '/') {
                while (cursor < text.length() && text.charAt(cursor) != '\n') cursor++;
                continue;
            }
            if (c == '/' && next == '*') {
                int start = cursor;
                cursor += 2; int end = text.indexOf("*/", cursor);
                if (end < 0) { issues.add(new Issue(start, 2, "cnpcplus.script.unclosed_comment", "/*")); cursor = text.length(); }
                else cursor = end + 2;
                continue;
            }
            if (c == '\'' || c == '"') { out[cursor] = '0'; quoted(c); regex = false; previous = "literal"; continue; }
            if (c == '`') { out[cursor++] = '0'; template(); regex = false; previous = "literal"; continue; }
            if (c == '/' && regex) {
                out[cursor++] = '0'; boolean characterClass = false;
                while (cursor < text.length()) {
                    char r = text.charAt(cursor++);
                    if (r == '\\') { cursor = Math.min(text.length(), cursor + 1); continue; }
                    if (r == '[') characterClass = true;
                    if (r == ']') characterClass = false;
                    if (r == '\n' || r == '\r') break;
                    if (r == '/' && !characterClass) {
                        while (cursor < text.length() && Character.isLetter(text.charAt(cursor))) cursor++;
                        break;
                    }
                }
                regex = false; previous = "literal"; continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int start = cursor++;
                while (cursor < text.length() && Character.isJavaIdentifierPart(text.charAt(cursor))) cursor++;
                text.getChars(start, cursor, out, start); boolean property = previous.equals(".");
                previous = text.substring(start, cursor); regex = !property && PREFIX.contains(previous); continue;
            }
            if (Character.isDigit(c)) {
                int start = cursor++;
                while (cursor < text.length() && (Character.isJavaIdentifierPart(text.charAt(cursor)) || text.charAt(cursor) == '.')) cursor++;
                text.getChars(start, cursor, out, start); regex = false; previous = "literal"; continue;
            }
            out[cursor++] = c;
            if (c == '{') { braces++; objectBraces.push(Arrays.asList("=", "(", "[", ",", ":", "return", "yield").contains(previous)); }
            if (c == '}') { if (interpolation && braces == 0) return; braces--; }
            if (c == '(') controlParens.push(CONTROL.contains(previous));
            if (c == ')') regex = !controlParens.isEmpty() && controlParens.pop();
            else if (c == '}') regex = objectBraces.isEmpty() || !objectBraces.pop();
            else if (c == '+' && next == '+' || c == '-' && next == '-') out[cursor++] = next;
            else regex = c != ']' && c != '.';
            previous = String.valueOf(c);
        }
    }
    private void quoted(char quote) {
        int start = cursor++; boolean closed = false;
        while (cursor < text.length()) {
            char c = text.charAt(cursor++);
            if (c == '\\') cursor = Math.min(text.length(), cursor + 1);
            else if (c == quote) { closed = true; break; }
            else if (c == '\n' || c == '\r') break;
        }
        if (!closed) issues.add(new Issue(start, 1, "cnpcplus.script.unclosed_string", String.valueOf(quote)));
    }
    private void template() {
        int start = cursor - 1; boolean closed = false;
        while (cursor < text.length()) {
            char c = text.charAt(cursor++);
            if (c == '\\') cursor = Math.min(text.length(), cursor + 1);
            else if (c == '`') { closed = true; break; }
            else if (c == '$' && cursor < text.length() && text.charAt(cursor) == '{') { out[cursor++] = '{'; code(true); }
        }
        if (!closed) issues.add(new Issue(start, 1, "cnpcplus.script.unclosed_string", "`"));
    }
}
