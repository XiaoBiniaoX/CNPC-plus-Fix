package bin.cnpcplus.transport.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import java.util.Arrays;

/** 样式与正文分离：start,end,RGB,flags；格式只在绘制时使用，不写进用户文本。 */
public final class TransportText {
    private TransportText() { }
    private static int[][] styles(String raw, int length) {
        int[][] styles = new int[2][length]; Arrays.fill(styles[0], -1);
        if (raw == null || raw.isEmpty()) return styles;
        int count = 0;
        for (String part : raw.split(";")) {
            if (++count > 512) break;
            String[] fields = part.split(","); if (fields.length != 4) continue;
            try {
                int start = Math.max(0, Integer.parseInt(fields[0].trim())), end = Math.min(length, Integer.parseInt(fields[1].trim()));
                int rgb = Integer.parseInt(fields[2].trim()), flags = Integer.parseInt(fields[3].trim()) & 7;
                for (int i = start; i < end; i++) { if (rgb >= 0) styles[0][i] = rgb & 0xFFFFFF; styles[1][i] |= flags; }
            } catch (NumberFormatException ignored) { }
        }
        return styles;
    }
    /** 文本长度变化后把样式裁到新长度：否则删掉的字数会把旧颜色留给之后新输入的字。 */
    public static String trim(String raw, int length) { return apply(raw, length, 0, 0, -1, 0, false); }
    public static String apply(String raw, int length, int start, int end, int color, int flag, boolean reset) {
        start = Math.max(0, Math.min(length, start)); end = Math.max(start, Math.min(length, end));
        if (start == end) { start = 0; end = length; }
        int[][] styles = styles(raw, length);
        boolean remove = flag != 0;
        for (int i = start; i < end; i++) remove &= (styles[1][i] & flag) == flag;
        for (int i = start; i < end; i++) {
            if (reset) { styles[0][i] = -1; styles[1][i] = 0; }
            else {
                if (color >= 0) styles[0][i] = color & 0xFFFFFF;
                if (flag != 0) styles[1][i] = remove ? styles[1][i] & ~flag : styles[1][i] | flag;
            }
        }
        StringBuilder result = new StringBuilder();
        for (int from = 0; from < length;) {
            int to = from + 1;
            while (to < length && styles[0][to] == styles[0][from] && styles[1][to] == styles[1][from]) to++;
            if (styles[0][from] >= 0 || styles[1][from] != 0) {
                if (result.length() > 0) result.append(';');
                result.append(from).append(',').append(to).append(',').append(styles[0][from]).append(',').append(styles[1][from]);
            }
            from = to;
        }
        return result.toString();
    }

    public static void draw(FontRenderer font, String text, String spans, int x, int y, int size, int color, int maxWidth) {
        if (text == null || text.isEmpty()) return;
        float scale = Math.max(1, size) / (float) font.FONT_HEIGHT;
        int[][] styles = styles(spans, text.length());
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, 0); GlStateManager.scale(scale, scale, 1);
        int penX = 0, penY = 0, legacyColor = color, legacyFlags = 0;
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int digit = Character.digit(code, 16);
                if (digit >= 0) { legacyColor = font.getColorCode(code); legacyFlags = 0; i += 2; continue; }
                if (code == 'r') { legacyColor = color; legacyFlags = 0; i += 2; continue; }
                if (code == 'l' || code == 'n' || code == 'o') { legacyFlags |= code == 'l' ? 1 : code == 'n' ? 2 : 4; i += 2; continue; }
                if (code == 'x' && i + 13 < text.length()) {
                    StringBuilder hex = new StringBuilder();
                    for (int j = i + 3; j <= i + 13; j += 2) hex.append(text.charAt(j));
                    try { legacyColor = Integer.parseInt(hex.toString(), 16); i += 14; continue; }
                    catch (NumberFormatException ignored) { }
                }
            }
            if (c == '\n') { penX = 0; penY += font.FONT_HEIGHT + 2; i++; continue; }
            int length = Character.charCount(text.codePointAt(i));
            int flags = styles[1][i] | legacyFlags;
            String prefix = ((flags & 1) != 0 ? "\u00a7l" : "") + ((flags & 2) != 0 ? "\u00a7n" : "") + ((flags & 4) != 0 ? "\u00a7o" : "");
            String glyph = prefix + text.substring(i, i + length);
            int width = font.getStringWidth(glyph);
            if (maxWidth > 0 && penX > 0 && (penX + width) * scale > maxWidth) { penX = 0; penY += font.FONT_HEIGHT + 2; }
            font.drawString(glyph, penX, penY, styles[0][i] >= 0 ? styles[0][i] : legacyColor);
            penX += width; i += length;
        }
        GlStateManager.popMatrix();
    }
}
