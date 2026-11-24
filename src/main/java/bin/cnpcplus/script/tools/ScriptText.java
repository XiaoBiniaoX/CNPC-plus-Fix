package bin.cnpcplus.script.tools;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.BreakIterator;

public final class ScriptText {
    public final ScriptMeasure Measure = new ScriptMeasure();
    private static final Pattern FORMAT = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z_0-9.]*)}");
    public static final class FormatToken {
        private final String text; private final boolean isCode;
        public FormatToken(String text, boolean isCode) { this.text = text; this.isCode = isCode; }
        public String text() { return text; } public boolean isCode() { return isCode; }
    }
    public static final class Template {
        private final String source;
        public Template(String source) { this.source = source; }
        public String source() { return source; }
    }
    public boolean isBlank(String value) { return value == null || value.codePoints().allMatch(Character::isWhitespace); }
    public String trim(String value) {
        int start = 0, end = value.length();
        while (start < end && Character.isWhitespace(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (end > start && Character.isWhitespace(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }
    public String truncate(String value, int maxLength) {
        if (maxLength < 0) throw new IllegalArgumentException("maxLength must be nonnegative");
        int end = Math.min(value.length(), maxLength);
        if (end > 0 && end < value.length() && Character.isSurrogatePair(value.charAt(end - 1), value.charAt(end))) end--;
        return value.substring(0, end);
    }
    public int lengthCodePoints(String value) { return value.codePointCount(0, value.length()); }
    public String truncateCodePoints(String value, int maxCodePoints) {
        if (maxCodePoints < 0) throw new IllegalArgumentException("maxCodePoints must be nonnegative");
        return value.substring(0, value.offsetByCodePoints(0, Math.min(maxCodePoints, lengthCodePoints(value))));
    }
    private BreakIterator graphemes(String value) { BreakIterator it = BreakIterator.getCharacterInstance(Locale.ROOT); it.setText(value); return it; }
    public int lengthGraphemes(String value) {
        BreakIterator it = graphemes(value); int count = 0;
        while (it.next() != BreakIterator.DONE) count++;
        return count;
    }
    public String truncateGraphemes(String value, int maxGraphemes) {
        if (maxGraphemes < 0) throw new IllegalArgumentException("maxGraphemes must be nonnegative");
        BreakIterator it = graphemes(value); int end = 0;
        for (int i = 0; i < maxGraphemes; i++) { int next = it.next(); if (next == BreakIterator.DONE) break; end = next; }
        return value.substring(0, end);
    }
    public String padStart(String value, int length, String pad) { return padding(value, length, pad) + value; }
    public String padEnd(String value, int length, String pad) { return value + padding(value, length, pad); }
    private String padding(String value, int length, String pad) {
        int missing = Math.max(0, length - value.length());
        if (pad.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        while (out.length() < missing) out.append(pad);
        return truncate(out.toString(), missing);
    }
    public String[] split(String value, String delimiter) { return value.split(Pattern.quote(delimiter), -1); }
    public String join(String[] values, String delimiter) { return String.join(delimiter, values); }
    public String replace(String value, String search, String replacement) { return value.replace(search, replacement); }
    public String normalize(String value, String form) { return Normalizer.normalize(value, Normalizer.Form.valueOf(form.toUpperCase(Locale.ROOT))); }
    public boolean equalsIgnoreCase(String a, String b) { return a.equalsIgnoreCase(b); }
    public boolean containsIgnoreCase(String value, String search) {
        for (int i = 0; i <= value.length() - search.length(); i++) if (value.regionMatches(true, i, search, 0, search.length())) return true;
        return false;
    }
    public String stripCodes(String value) { return FORMAT.matcher(value).replaceAll(""); }
    public String translate(String value, String marker) {
        if (marker.isEmpty()) throw new IllegalArgumentException("marker cannot be empty");
        return value.replaceAll("(?i)" + Pattern.quote(marker) + "([0-9a-fk-or])", "§$1");
    }
    public List<FormatToken> parse(String value) {
        List<FormatToken> tokens = new ArrayList<>(); Matcher matcher = FORMAT.matcher(value); int end = 0;
        while (matcher.find()) {
            if (end < matcher.start()) tokens.add(new FormatToken(value.substring(end, matcher.start()), false));
            tokens.add(new FormatToken(matcher.group(), true)); end = matcher.end();
        }
        if (end < value.length()) tokens.add(new FormatToken(value.substring(end), false));
        return Collections.unmodifiableList(tokens);
    }
    public Template compile(String template) { return new Template(template); }
    public String render(Template compiled, Map<String, ?> variables) {
        Matcher matcher = PLACEHOLDER.matcher(compiled.source); StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : value.toString()));
        }
        matcher.appendTail(result); return result.toString();
    }
    public List<String> wrap(String value, int maxWidth) {
        if (maxWidth < 1) throw new IllegalArgumentException("maxWidth must be positive");
        List<String> result = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            BreakIterator it = graphemes(line); int start = 0, previous = 0, count = 0, end;
            while ((end = it.next()) != BreakIterator.DONE) {
                if (count++ == maxWidth) { result.add(line.substring(start, previous)); start = previous; count = 1; }
                previous = end;
            }
            result.add(line.substring(start));
        }
        return Collections.unmodifiableList(result);
    }
}
