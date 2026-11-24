package top.cnpcplus.script.tools;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScriptText {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final Pattern FORMAT = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z_0-9.]*)}");
    public record FormatToken(String text, boolean isCode) {}
    public record Template(String source) {}

    public boolean isBlank(String value) { return value == null || value.isBlank(); }
    public String trim(String value) { return value.strip(); }
    public String truncate(String value, int maxLength) {
        if (maxLength < 0) throw new IllegalArgumentException("maxLength must be nonnegative");
        int end = Math.min(value.length(), maxLength);
        if (end > 0 && end < value.length() && Character.isSurrogatePair(value.charAt(end - 1), value.charAt(end))) end--;
        return value.substring(0, end);
    }
    public int lengthCodePoints(String value) { return value.codePointCount(0, value.length()); }
    public String truncateCodePoints(String value, int maxCodePoints) {
        return value.substring(0, value.offsetByCodePoints(0, Math.min(maxCodePoints, lengthCodePoints(value))));
    }
    public int lengthGraphemes(String value) { return (int) GRAPHEME.matcher(value).results().count(); }
    public String truncateGraphemes(String value, int maxGraphemes) {
        if (maxGraphemes < 0) throw new IllegalArgumentException("maxGraphemes must be nonnegative");
        Matcher matcher = GRAPHEME.matcher(value);
        int end = 0;
        for (int i = 0; i < maxGraphemes && matcher.find(); i++) end = matcher.end();
        return value.substring(0, end);
    }
    public String padStart(String value, int length, String pad) { return padding(value, length, pad) + value; }
    public String padEnd(String value, int length, String pad) { return value + padding(value, length, pad); }
    private String padding(String value, int length, String pad) {
        int missing = Math.max(0, length - value.length());
        return pad.isEmpty() ? "" : truncate(pad.repeat(missing / pad.length() + 1), missing);
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
        List<FormatToken> tokens = new ArrayList<>();
        Matcher matcher = FORMAT.matcher(value);
        int end = 0;
        while (matcher.find()) {
            if (end < matcher.start()) tokens.add(new FormatToken(value.substring(end, matcher.start()), false));
            tokens.add(new FormatToken(matcher.group(), true));
            end = matcher.end();
        }
        if (end < value.length()) tokens.add(new FormatToken(value.substring(end), false));
        return List.copyOf(tokens);
    }
    public Template compile(String template) { return new Template(template); }
    public String render(Template compiled, Map<String, ?> variables) {
        return PLACEHOLDER.matcher(compiled.source).replaceAll(match -> {
            Object value = variables.get(match.group(1));
            return Matcher.quoteReplacement(value == null ? match.group() : value.toString());
        });
    }
    public List<String> wrap(String value, int maxWidth) {
        if (maxWidth < 1) throw new IllegalArgumentException("maxWidth must be positive");
        List<String> result = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            Matcher matcher = GRAPHEME.matcher(line);
            int start = 0, i = 0;
            while (matcher.find()) {
                if (i++ == maxWidth) {
                    result.add(line.substring(start, matcher.start()));
                    start = matcher.start();
                    i = 1;
                }
            }
            result.add(line.substring(start));
        }
        return List.copyOf(result);
    }
}
