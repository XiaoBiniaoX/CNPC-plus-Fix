package top.cnpcplus.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import top.cnpcplus.transport.TransportSpans;
import top.cnpcplus.transport.TransportSpans.Span;

/** Plain source text, immutable style snapshot and source-indexed, font-measured wrapping. */
public record StyledText(String text, String spans) {
    public StyledText {
        text = text == null ? "" : text;
        spans = spans == null ? "" : spans;
    }

    public StyledText apply(int start, int end, int rgb, int flags, boolean toggle) {
        return new StyledText(text, TransportSpans.apply(spans, Math.max(0, start), Math.min(text.length(), end), rgb, flags, toggle));
    }

    public StyledText edit(String next) {
        next = next == null ? "" : next;
        if (text.equals(next)) return this;
        int start = 0, oldEnd = text.length(), newEnd = next.length();
        while (start < oldEnd && start < newEnd && text.charAt(start) == next.charAt(start)) start++;
        if (start > 0 && start < oldEnd && Character.isLowSurrogate(text.charAt(start))) start--;
        while (oldEnd > start && newEnd > start && text.charAt(oldEnd - 1) == next.charAt(newEnd - 1)) { oldEnd--; newEnd--; }
        if (oldEnd < text.length() && oldEnd > start && Character.isLowSurrogate(text.charAt(oldEnd))) { oldEnd++; newEnd++; }
        return replace(start, oldEnd, next.substring(start, newEnd));
    }

    public StyledText replace(int start, int end, String replacement) {
        int added = replacement.length(), delta = added - (end - start);
        List<Span> source = TransportSpans.parse(spans), out = new ArrayList<>();
        for (Span span : source) {
            int s = Math.max(0, span.start), e = Math.min(text.length(), span.end);
            if (s < start && e > s) out.add(new Span(s, Math.min(e, start), span.rgb, span.flags));
            if (e > end) out.add(new Span(Math.max(s, end) + delta, e + delta, span.rgb, span.flags));
        }
        if (added > 0) {
            int at = start < text.length() ? start : start - 1;
            int color = TransportSpans.colorAt(source, at, -1), flags = TransportSpans.flagsAt(source, at);
            if (color >= 0 || flags != 0) out.add(new Span(start, start + added, color, flags));
        }
        return new StyledText(text.substring(0, start) + replacement + text.substring(end), TransportSpans.serialize(out));
    }

    public StyledText replaceAll(String token, String replacement) {
        if (token.isEmpty()) return this;
        StyledText value = this;
        int pos = 0;
        while ((pos = value.text.indexOf(token, pos)) >= 0) {
            value = value.replace(pos, pos + token.length(), replacement);
            pos += replacement.length();
        }
        return value;
    }

    /** Legacy codes are interpreted only in the display snapshot; editable source stays untouched. */
    public StyledText legacy() {
        String[] legacy = TransportSpans.fromLegacy(text);
        if (legacy[0].equals(text)) return this;
        StyledText adjusted = this;
        int source = 0, removed = 0;
        while (source < text.length()) {
            int length = TransportSpans.legacyCodeLength(text, source);
            if (length > 0) {
                adjusted = adjusted.replace(source - removed, source - removed + length, "");
                source += length;
                removed += length;
            } else source++;
        }
        return new StyledText(legacy[0], TransportSpans.merge(legacy[1], adjusted.spans));
    }

    public Component component() { return component(0, text.length(), TransportSpans.parse(spans)); }

    public Component component(int start, int end) { return component(start, end, TransportSpans.parse(spans)); }

    private Component component(int start, int end, List<Span> styles) {
        MutableComponent out = Component.empty();
        for (int i = start; i < end;) {
            Style style = styleAt(styles, i);
            int j = i + Character.charCount(text.codePointAt(i));
            while (j < end && style.equals(styleAt(styles, j))) j += Character.charCount(text.codePointAt(j));
            out.append(Component.literal(text.substring(i, j)).setStyle(style));
            i = j;
        }
        return out;
    }

    private static Style styleAt(List<Span> styles, int index) {
        int color = TransportSpans.colorAt(styles, index, -1), flags = TransportSpans.flagsAt(styles, index);
        Style style = Style.EMPTY.withBold((flags & TransportSpans.FLAG_BOLD) != 0)
                .withUnderlined((flags & TransportSpans.FLAG_UNDERLINE) != 0)
                .withItalic((flags & TransportSpans.FLAG_ITALIC) != 0);
        return color < 0 ? style : style.withColor(color);
    }

    public record Line(int start, int end, Component component, double width) {}

    /** No lost characters, including wrap spaces; CRLF counts as one explicit break. */
    public List<Line> lines(double maxWidth, ToDoubleFunction<Component> measure) {
        List<Span> styles = TransportSpans.parse(spans);
        List<Line> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int i = start, breakAt = -1;
            while (i < text.length()) {
                int cp = text.codePointAt(i);
                if (cp == '\n' || cp == '\r') break;
                int next = i + Character.charCount(cp);
                // ponytail: candidate measurement is quadratic within one short GUI line;
                // preserves native run/font rounding rather than summing rounded glyph widths.
                double width = measure.applyAsDouble(component(start, next, styles));
                if (maxWidth > 0 && width > maxWidth && i > start) {
                    if (breakAt > start) i = breakAt;
                    break;
                }
                i = next;
                if (Character.isWhitespace(cp)) breakAt = i;
            }
            Component line = component(start, i, styles);
            out.add(new Line(start, i, line, measure.applyAsDouble(line)));
            start = i;
            if (start < text.length() && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
                char newline = text.charAt(start++);
                if (newline == '\r' && start < text.length() && text.charAt(start) == '\n') start++;
                if (start == text.length()) out.add(new Line(start, start, Component.empty(), 0));
            }
        }
        if (out.isEmpty()) out.add(new Line(0, 0, Component.empty(), 0));
        return out;
    }
}
