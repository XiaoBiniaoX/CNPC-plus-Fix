package bin.cnpcplus.text;

import java.util.Arrays;

/** Plain-source UTF-16 ranges: start,end,RGB,flags. Shared by storage and both editors. */
public final class TextSpans {
    private TextSpans() { }
    public static int[][] read(String raw, int length) {
        int[][] values = new int[2][length]; Arrays.fill(values[0], -1);
        for (String part : raw.split(";")) {
            String[] fields = part.split(",");
            if (fields.length != 4) continue;
            try {
                int start = Math.max(0, Integer.parseInt(fields[0].trim())), end = Math.min(length, Integer.parseInt(fields[1].trim()));
                int rgb = Integer.parseInt(fields[2].trim()), flags = Integer.parseInt(fields[3].trim()) & 7;
                for (int i = start; i < end; i++) { if (rgb >= 0) values[0][i] = rgb & 0xFFFFFF; values[1][i] |= flags; }
            } catch (NumberFormatException ignored) { /* Invalid external ranges do not change the source text. */ }
        }
        return values;
    }
    public static String write(int[][] values) {
        StringBuilder out = new StringBuilder(); int length = values[0].length;
        for (int from = 0; from < length;) {
            int to = from + 1;
            while (to < length && values[0][to] == values[0][from] && values[1][to] == values[1][from]) to++;
            if (values[0][from] >= 0 || values[1][from] != 0) {
                if (out.length() > 0) out.append(';');
                out.append(from).append(',').append(to).append(',').append(values[0][from]).append(',').append(values[1][from]);
            }
            from = to;
        }
        return out.toString();
    }
    public static String apply(String raw, int length, int start, int end, int color, int flag, boolean reset) {
        start = Math.max(0, Math.min(length, start)); end = Math.max(start, Math.min(length, end));
        int[][] values = read(raw, length); boolean remove = flag != 0;
        for (int i = start; i < end; i++) remove &= (values[1][i] & flag) == flag;
        for (int i = start; i < end; i++) {
            if (reset) { values[0][i] = -1; values[1][i] = 0; }
            else {
                if (color >= 0) values[0][i] = color & 0xFFFFFF;
                else if (color == -2) values[0][i] = -1;
                if (flag != 0) values[1][i] = remove ? values[1][i] & ~flag : values[1][i] | flag;
            }
        }
        return write(values);
    }
}
