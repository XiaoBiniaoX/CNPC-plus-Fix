package top.cnpcplus.transport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import top.cnpcplus.transport.TransportKeys;

/**
 * 任务241z：传送点图片绘制（玩家面板 + 编辑页共用）。
 *
 * <h2>1.20.1 blit 的两种重载（反编译实锤）</h2>
 * <pre>
 *  9 参  blit(tex, x, y, u, v, w, h, texW, texH)        ← **不能缩放**：目标尺寸 = 源尺寸 = w×h
 * 11 参  blit(tex, x, y, dw, dh, u, v, srcW, srcH, texW, texH)  ← 可缩放：目标 dw×dh，源 srcW×srcH
 * </pre>
 * 要缩放必须用 11 参。
 *
 * <h2>显示模式</h2>
 * 原尺寸（MODE_CENTER：不放大，超出框就等比缩小，居中）/ 拉伸（MODE_STRETCH，可变形）/
 * 适应（MODE_FIT：等比缩放到尽量填满框，可放大）/ 平铺（MODE_TILE）。
 * 旧数据 MODE_FILL 视为「适应」。IMG_X/IMG_Y 对所有模式生效（平移，可负）。
 *
 * <h2>裁剪</h2>
 * 本类**不做裁剪**：调用方必须先 enableScissor 到图片框（设计坐标要换算成屏幕坐标），
 * 否则原尺寸/平铺/偏移会把图画出框外。
 */
public final class TransportImage {
    private TransportImage() {}

    /** 平铺时最多允许的瓦片数（防止 1px 图把帧率拖死；超限回退为适应）。 */
    private static final int MAX_TILES = 8192;

    public static void draw(GuiGraphics g, ResourceLocation tex, int srcW, int srcH,
                            int x, int y, int w, int h, int mode, int offX, int offY) {
        if (g == null || tex == null || w <= 0 || h <= 0) return;
        if (srcW <= 0 || srcH <= 0) return;
        if (mode == TransportKeys.MODE_FILL) mode = TransportKeys.MODE_FIT;

        switch (mode) {
            case TransportKeys.MODE_STRETCH -> {
                g.blit(tex, x + offX, y + offY, w, h, 0.0F, 0.0F, srcW, srcH, srcW, srcH);
            }
            case TransportKeys.MODE_CENTER -> {
                double s = Math.min(1.0, Math.min(w / (double) srcW, h / (double) srcH));
                int dw = Math.max(1, (int) Math.round(srcW * s));
                int dh = Math.max(1, (int) Math.round(srcH * s));
                int dx = x + (w - dw) / 2 + offX;
                int dy = y + (h - dh) / 2 + offY;
                g.blit(tex, dx, dy, dw, dh, 0.0F, 0.0F, srcW, srcH, srcW, srcH);
            }
            case TransportKeys.MODE_TILE -> {
                double s = Math.min(1.0, Math.min(w / (double) srcW, h / (double) srcH));
                int tileW = Math.max(1, (int) Math.round(srcW * s));
                int tileH = Math.max(1, (int) Math.round(srcH * s));

                int cols = (w + tileW - 1) / tileW + 1;
                int rows = (h + tileH - 1) / tileH + 1;
                if ((long) cols * rows > MAX_TILES) {
                    drawFit(g, tex, srcW, srcH, x, y, w, h, offX, offY);
                    return;
                }
                int startX = x + offX;
                int startY = y + offY;
                while (startX > x) startX -= tileW;
                while (startY > y) startY -= tileH;
                for (int ty = startY; ty < y + h; ty += tileH) {
                    for (int tx = startX; tx < x + w; tx += tileW) {
                        g.blit(tex, tx, ty, tileW, tileH, 0.0F, 0.0F, srcW, srcH, srcW, srcH);
                    }
                }
            }
            default -> drawFit(g, tex, srcW, srcH, x, y, w, h, offX, offY);
        }
    }

    /** 适应：等比完整显示，居中 + 偏移。 */
    private static void drawFit(GuiGraphics g, ResourceLocation tex, int srcW, int srcH,
                                int x, int y, int w, int h, int offX, int offY) {
        double s = Math.min(w / (double) srcW, h / (double) srcH);
        int dw = Math.max(1, (int) Math.round(srcW * s));
        int dh = Math.max(1, (int) Math.round(srcH * s));
        int dx = x + (w - dw) / 2 + offX;
        int dy = y + (h - dh) / 2 + offY;
        g.blit(tex, dx, dy, dw, dh, 0.0F, 0.0F, srcW, srcH, srcW, srcH);
    }
}
