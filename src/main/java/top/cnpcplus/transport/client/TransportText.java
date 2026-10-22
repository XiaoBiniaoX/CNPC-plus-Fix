package top.cnpcplus.transport.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 任务241j：按字号缩放绘制文字（默认字体字形高 9px，字号 N 即放大 N/9 倍）。
 * 面板内（设计空间 pose 已缩放）与屏幕空间（行按钮/白板）通用。
 * 241q：新增多行绘制 + 样式区间（颜色/粗体/下划线/斜体）绘制，正文保持干净。
 */
public final class TransportText {
    private TransportText() {}

    public static void draw(GuiGraphics g, Font font, String text, int x, int y, int size, int color) {
        draw(g, font, text, x, y, size / 9f, color, false);
    }

    public static void draw(GuiGraphics g, Font font, String text, int x, int y, float scale, int color, boolean shadow) {
        if (text == null || text.isEmpty() || scale <= 0f) return;
        if (Math.abs(scale - 1f) < 0.02f) {
            g.drawString(font, text, x, y, color, shadow);
            return;
        }
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    public static float fit(Font font, String text, int maxWidth) {
        int w = font.width(text == null ? "" : text);
        if (w <= 0) return 1f;
        return Math.min(1f, (float) maxWidth / w);
    }

    public static int width(Font font, String text, float scale) {
        return Math.round(font.width(text == null ? "" : text) * scale);
    }

    /**
     * 241q：多行绘制（按 \n 换行）。之前用单行 drawString → 用户输入的换行完全不生效。
     *
     * @return 绘制总高度（设计像素）
     */
    public static int drawMultiline(GuiGraphics g, Font font, String text, int x, int y,
                                     int size, int color, int maxWidth) {
        if (text == null || text.isEmpty()) return 0;
        int lineH = lineHeight(size);
        int total = 0;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            String shown = line;
            if (maxWidth > 0) {
                while (shown.length() > 1 && font.width(shown) * (size / 9f) > maxWidth) {
                    shown = shown.substring(0, shown.length() - 1);
                }
            }
            draw(g, font, shown, x, y + total, size, color);
            total += lineH;
        }
        return total;
    }

    /** 241r：行高（原来直接用字号 → 上下行重叠）。 */
    public static int lineHeight(int size) {
        return Math.max(1, size) + Math.max(2, size / 4);
    }

    /**
     * 241q：带样式的多行绘制（颜色/粗体/下划线/斜体由样式区间决定，正文保持干净）。
     *
     * @param spans 样式区间字符串（TransportSpans 格式），可为 null
     */
    public static int drawStyled(GuiGraphics g, Font font, String text, String spans,
                                 int x, int y, int size, int baseColor, int maxWidth) {
        if (text == null || text.isEmpty()) return 0;
        java.util.List<top.cnpcplus.transport.TransportSpans.Span> list =
                top.cnpcplus.transport.TransportSpans.parse(spans);
        int lineH = lineHeight(size);
        int total = 0;
        String[] lines = text.split("\n", -1);
        int base = 0;
        int px = x;
        for (String line : lines) {
            int lineStart = base;
            base += line.length() + 1;
            int i = 0;
            while (i < line.length()) {
                int rgb = top.cnpcplus.transport.TransportSpans.colorAt(list, lineStart + i, baseColor);
                int fl = top.cnpcplus.transport.TransportSpans.flagsAt(list, lineStart + i);
                int j = i;
                while (j < line.length()
                        && top.cnpcplus.transport.TransportSpans.colorAt(list, lineStart + j, baseColor) == rgb
                        && top.cnpcplus.transport.TransportSpans.flagsAt(list, lineStart + j) == fl) {
                    j++;
                }
                String seg = line.substring(i, j);
                if (maxWidth > 0) {
                    while (seg.length() > 1 && font.width(seg) * (size / 9f) > maxWidth) {
                        seg = seg.substring(0, seg.length() - 1);
                    }
                }
                // 241t：必须按段宽推进 x（之前每段都画在同一个 x → 前后文本重叠）
                draw(g, font, stylePrefix(rgb, fl, baseColor) + seg, px, y + total, size, baseColor);
                px += width(font, seg, size / 9f);
                i = j;
            }
            total += lineH;
        }
        return total;
    }

    /** 把颜色/标志转成 MC 格式码前缀（vanilla 字体原生支持，宽度计算会自动忽略）。 */
    public static String stylePrefix(int rgb, int flags, int baseColor) {
        StringBuilder sb = new StringBuilder();
        if (rgb >= 0 && (rgb & 0xFFFFFF) != (baseColor & 0xFFFFFF)) {
            String hex = String.format("%06X", rgb & 0xFFFFFF);
            sb.append('\u00a7').append('x');
            for (int i = 0; i < hex.length(); i++) {
                sb.append('\u00a7').append(hex.charAt(i));
            }
        }
        if ((flags & top.cnpcplus.transport.TransportSpans.FLAG_BOLD) != 0) sb.append('\u00a7').append('l');
        if ((flags & top.cnpcplus.transport.TransportSpans.FLAG_UNDERLINE) != 0) sb.append('\u00a7').append('n');
        if ((flags & top.cnpcplus.transport.TransportSpans.FLAG_ITALIC) != 0) sb.append('\u00a7').append('o');
        return sb.toString();
    }
}
