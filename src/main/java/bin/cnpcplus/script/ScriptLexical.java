package bin.cnpcplus.script;

import java.util.*;

/** Offset-preserving lexical masking; no engine, script execution or API calls. */
final class ScriptLexical {
    private static final List<String> PREFIX = Arrays.asList("return", "throw", "case", "delete", "typeof", "void", "new", "yield", "else", "do", "in", "of", "instanceof", "await");
    private static final List<String> CONTROL = Arrays.asList("if", "while", "for", "switch", "catch", "with");
    private final String text;
    private final char[] out;
    private int cursor;
    private ScriptLexical(String text) {
        this.text = text; out = new char[text.length()]; Arrays.fill(out, ' ');
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n' || text.charAt(i) == '\r') out[i] = text.charAt(i);
    }
    static String mask(String text) {
        ScriptLexical lexer = new ScriptLexical(text); lexer.code(false); return new String(lexer.out);
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
                cursor += 2; int end = text.indexOf("*/", cursor); cursor = end < 0 ? text.length() : end + 2; continue;
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
        cursor++;
        while (cursor < text.length()) {
            char c = text.charAt(cursor++);
            if (c == '\\') cursor = Math.min(text.length(), cursor + 1);
            else if (c == quote || c == '\n' || c == '\r') return;
        }
    }
    private void template() {
        while (cursor < text.length()) {
            char c = text.charAt(cursor++);
            if (c == '\\') cursor = Math.min(text.length(), cursor + 1);
            else if (c == '`') return;
            else if (c == '$' && cursor < text.length() && text.charAt(cursor) == '{') { out[cursor++] = '{'; code(true); }
        }
    }
}
