package bin.cnpcplus.transport.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import bin.cnpcplus.text.TextSpans;

/** 样式与正文分离：start,end,RGB,flags；格式只在绘制时使用，不写进用户文本。 */
public final class TransportText {
    private TransportText() { }
    private static int[][] styles(String raw, int length) {
        return TextSpans.read(raw == null ? "" : raw, length);
    }
    /** 文本长度变化后把样式裁到新长度：否则删掉的字数会把旧颜色留给之后新输入的字。 */
    public static String trim(String raw, int length) {
        int size = Math.max(0, length);
        return serialize(styles(raw, size), size);
    }
    /** 给选区上色（IBE 的 addColorFormatting）：只改颜色，不动粗体/斜体。 */
    public static String color(String raw, int length, int start, int end, int rgb) {
        return apply(raw, length, start, end, rgb & 0xFFFFFF, 0, false);
    }
    /** 清除选区颜色（IBE 的 removeColorFormatting）：把这段从颜色区间里挖掉，其余颜色保留。 */
    public static String clear(String raw, int length, int start, int end) {
        return TextSpans.apply(raw, length, start, end, -2, 0, false);
    }
    /** 切换选区样式；reset=true 时把选区恢复成默认（颜色 + 全部样式）。 */
    public static String style(String raw, int length, int start, int end, int flag, boolean reset) {
        return apply(raw, length, start, end, -1, flag, reset);
    }
    public static String apply(String raw, int length, int start, int end, int color, int flag, boolean reset) {
        return TextSpans.apply(raw, length, start, end, color, flag, reset);
    }

    /** 逐字符样式重新压成区间串；相邻同值自动合并，越界区间自然消失。 */
    private static String serialize(int[][] styles, int length) {
        return TextSpans.write(styles);
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
