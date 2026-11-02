package bin.cnpcplus.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Immutable text/style snapshot; no client classes may enter persistence or copying. */
public final class StyledText {
    public final String text, spans;
    public StyledText(String text, String spans) {
        this.text = text == null ? "" : text; this.spans = spans == null ? "" : spans;
    }
    public StyledText apply(int start, int end, int rgb, int flags, boolean reset) {
        return new StyledText(text, TextSpans.apply(spans, text.length(), start, end, rgb, flags, reset));
    }
    public StyledText edit(String next) {
        if (text.equals(next)) return this;
        int start = 0, oldEnd = text.length(), newEnd = next.length();
        while (start < oldEnd && start < newEnd && text.charAt(start) == next.charAt(start)) start++;
        if (start > 0 && start < oldEnd && Character.isLowSurrogate(text.charAt(start))) start--;
        while (oldEnd > start && newEnd > start && text.charAt(oldEnd - 1) == next.charAt(newEnd - 1)) { oldEnd--; newEnd--; }
        if (oldEnd < text.length() && oldEnd > start && Character.isLowSurrogate(text.charAt(oldEnd))) { oldEnd++; newEnd++; }
        int[][] old = TextSpans.read(spans, text.length()), values = new int[2][next.length()];
        int at = start < text.length() ? start : start - 1;
        for (int kind = 0; kind < 2; kind++) {
            System.arraycopy(old[kind], 0, values[kind], 0, start);
            java.util.Arrays.fill(values[kind], start, newEnd, at >= 0 ? old[kind][at] : kind == 0 ? -1 : 0);
            System.arraycopy(old[kind], oldEnd, values[kind], newEnd, text.length() - oldEnd);
        }
        return new StyledText(next, TextSpans.write(values));
    }
    /** Translate native placeholders independently, keeping range indices aligned. */
    public StyledText format(java.util.function.Function<String, String> formatter) {
        StyledText value = this;
        for (String token : new String[]{"{player}", "@npc", "@p"}) {
            String replacement = formatter.apply(token);
            if (replacement.equals(token)) continue;
            int from = 0, at;
            while ((at = value.text.indexOf(token, from)) >= 0) {
                String next = value.text.substring(0, at) + replacement + value.text.substring(at + token.length());
                value = value.edit(next); from = at + replacement.length();
            }
        }
        return value.edit(formatter.apply(value.text));
    }
    /** Interpret existing native formatting in display only; never insert it into editable text. */
    public StyledText legacy() {
        int[][] source = TextSpans.read(spans, text.length()), values = new int[2][text.length()];
        StringBuilder clean = new StringBuilder(); int color = -1, flags = 0;
        int[] palette = {0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
                0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
        for (int i = 0; i < text.length();) {
            if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1)); int digit = Character.digit(code, 16);
                if (digit >= 0) { color = palette[digit]; flags = 0; i += 2; continue; }
                if (code == 'r') { color = -1; flags = 0; i += 2; continue; }
                if (code == 'l' || code == 'n' || code == 'o') { flags |= code == 'l' ? 1 : code == 'n' ? 2 : 4; i += 2; continue; }
                if (code == 'x' && i + 13 < text.length()) {
                    int rgb = 0; boolean valid = true;
                    for (int j = i + 2; j < i + 14; j += 2) {
                        int hex = Character.digit(text.charAt(j + 1), 16);
                        valid &= text.charAt(j) == '\u00a7' && hex >= 0; rgb = rgb << 4 | Math.max(hex, 0);
                    }
                    if (valid) { color = rgb; i += 14; continue; }
                }
            }
            int at = clean.length(); clean.append(text.charAt(i));
            values[0][at] = source[0][i] >= 0 ? source[0][i] : color;
            values[1][at] = source[1][i] | flags; i++;
        }
        if (clean.length() == text.length()) return this;
        return new StyledText(clean.toString(), TextSpans.write(new int[][]{
                java.util.Arrays.copyOf(values[0], clean.length()), java.util.Arrays.copyOf(values[1], clean.length())}));
    }
    public static final class Row {
        public final int start, end;
        public final String text;
        public Row(String text, int start, int end) { this.text = text.substring(start, end); this.start = start; this.end = end; }
    }
    /** Preserve every character, wrap spaces and empty final rows; never split a surrogate pair. */
    public List<Row> rows(int maxWidth, ToIntFunction<String> measure) {
        List<Row> rows = new ArrayList<>(); int start = 0;
        while (start < text.length()) {
            int end = start, space = -1;
            while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
                int next = end + Character.charCount(text.codePointAt(end));
                // ponytail: one short GUI row uses native whole-run rounding; O(row length squared).
                if (maxWidth > 0 && measure.applyAsInt(text.substring(start, next)) > maxWidth && end > start) {
                    if (space > start) end = space;
                    break;
                }
                end = next; if (Character.isWhitespace(text.codePointBefore(end))) space = end;
            }
            rows.add(new Row(text, start, end)); start = end;
            if (start < text.length() && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
                char newline = text.charAt(start++);
                if (newline == '\r' && start < text.length() && text.charAt(start) == '\n') start++;
                if (start == text.length()) rows.add(new Row(text, start, start));
            }
        }
        if (rows.isEmpty()) rows.add(new Row(text, 0, 0));
        return rows;
    }
}
