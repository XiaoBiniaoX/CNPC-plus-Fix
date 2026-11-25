package bin.cnpcplus.script;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/** Offset-preserving lexical masking, shared by completion and the non-executing simple check. */
public final class ScriptLexical {
    public record Issue(int offset, int length, String kind, String subject) {}
    private final List<Issue> issues = new ArrayList<>();
    private static final Set<String> PREFIX = Set.of("return", "throw", "case", "delete", "typeof",
            "void", "new", "yield", "else", "do", "in", "of", "instanceof", "await");
    private static final Set<String> CONTROL = Set.of("if", "while", "for", "switch", "catch", "with");
    private final String text;
    private final char[] out;
    private int cursor;

    private ScriptLexical(String text) {
        this.text = text;
        out = new char[text.length()];
        Arrays.fill(out, ' ');
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\n' || text.charAt(i) == '\r') out[i] = text.charAt(i);
    }

    public static String mask(String text) {
        ScriptLexical lexer = new ScriptLexical(text);
        lexer.code(false);
        return new String(lexer.out);
    }

    public static List<Issue> issues(String text) {
        ScriptLexical lexer = new ScriptLexical(text);
        lexer.code(false);
        return List.copyOf(lexer.issues);
    }

    private void code(boolean interpolation) {
        boolean regex = true;
        String previous = "";
        int braces = 0;
        Deque<Boolean> controlParens = new ArrayDeque<>();
        Deque<Boolean> objectBraces = new ArrayDeque<>();
        while (cursor < text.length()) {
            char c = text.charAt(cursor), next = cursor + 1 < text.length() ? text.charAt(cursor + 1) : 0;
            if (Character.isWhitespace(c)) { cursor++; continue; }
            if (c == '/' && next == '/') {
                while (cursor < text.length() && text.charAt(cursor) != '\n') cursor++;
                continue;
            }
            if (c == '/' && next == '*') {
                int start = cursor;
                int end = text.indexOf("*/", cursor + 2);
                if (end < 0) issues.add(new Issue(start, 2, "unclosed_comment", "/*"));
                cursor = end < 0 ? text.length() : end + 2;
                continue;
            }
            if (c == '\'' || c == '"') {
                int start = cursor;
                out[cursor] = '0';
                if (!quoted(c)) issues.add(new Issue(start, 1, "unclosed_string", String.valueOf(c)));
                regex = false; previous = "literal"; continue;
            }
            if (c == '`') {
                int start = cursor;
                out[cursor++] = '0';
                if (!template()) issues.add(new Issue(start, 1, "unclosed_string", "`"));
                else issues.add(new Issue(start, 1, "template_literal", "`"));
                regex = false; previous = "literal"; continue;
            }
            if (c == '/' && regex) {
                out[cursor++] = '0';
                boolean characterClass = false;
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
                text.getChars(start, cursor, out, start);
                boolean property = previous.equals(".");
                previous = text.substring(start, cursor);
                regex = !property && PREFIX.contains(previous);
                continue;
            }
            if (Character.isDigit(c)) {
                int start = cursor++;
                while (cursor < text.length() && (Character.isJavaIdentifierPart(text.charAt(cursor)) || text.charAt(cursor) == '.')) cursor++;
                text.getChars(start, cursor, out, start);
                regex = false; previous = "literal"; continue;
            }
            out[cursor++] = c;
            if (c == '{') {
                braces++;
                objectBraces.push(Set.of("=", "(", "[", ",", ":", "return", "yield").contains(previous));
            }
            if (c == '}') { if (interpolation && braces == 0) return; braces--; }
            if (c == '(') controlParens.push(CONTROL.contains(previous));
            if (c == ')') regex = !controlParens.isEmpty() && controlParens.pop();
            else if (c == '}') regex = objectBraces.isEmpty() || !objectBraces.pop();
            else if (c == '+' && next == '+' || c == '-' && next == '-') out[cursor++] = next;
            else regex = c != ']' && c != '.';
            previous = String.valueOf(c);
        }
    }

    private boolean quoted(char quote) {
        cursor++;
        while (cursor < text.length()) {
            char c = text.charAt(cursor++);
            if (c == '\\') cursor = Math.min(text.length(), cursor + 1);
            else if (c == quote) return true;
            else if (c == '\n' || c == '\r') return false;
        }
        return false;
    }

    private boolean template() {
        while (cursor < text.length()) {
            char c = text.charAt(cursor++);
            if (c == '\\') cursor = Math.min(text.length(), cursor + 1);
            else if (c == '`') return true;
            else if (c == '$' && cursor < text.length() && text.charAt(cursor) == '{') { out[cursor++] = '{'; code(true); }
        }
        return false;
    }
}
