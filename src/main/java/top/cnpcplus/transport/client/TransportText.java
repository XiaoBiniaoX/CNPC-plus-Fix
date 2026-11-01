package top.cnpcplus.transport.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import top.cnpcplus.text.StyledText;

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
        return drawStyled(g, font, text, "", x, y, size, color, maxWidth);
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
        if (text == null || text.isEmpty() || size <= 0) return 0;
        float scale = size / 9f;
        var lines = new StyledText(text, spans).legacy().lines(maxWidth > 0 ? maxWidth / scale : 0,
                component -> font.width(component));
        var pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1f);
            for (int row = 0; row < lines.size(); row++) {
                g.drawString(font, lines.get(row).component(), 0, Math.round(row * lineHeight(size) / scale), baseColor, false);
            }
        } finally {
            pose.popPose();
        }
        return lines.size() * lineHeight(size);
    }

    /** 把标志转成 MC 格式码前缀（颜色由 draw 的 color 参数直接传，避免 §x 不兼容）。 */
    public static String stylePrefix(int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & top.cnpcplus.transport.TransportSpans.FLAG_BOLD) != 0) sb.append('\u00a7').append('l');
        if ((flags & top.cnpcplus.transport.TransportSpans.FLAG_UNDERLINE) != 0) sb.append('\u00a7').append('n');
        if ((flags & top.cnpcplus.transport.TransportSpans.FLAG_ITALIC) != 0) sb.append('\u00a7').append('o');
        return sb.toString();
    }
}
