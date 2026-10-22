package top.cnpcplus.transport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 任务241z：传送点图片绘制（玩家面板 + 编辑页共用）——**重写版**。
 *
 * <h2>1.20.1 blit 的三种重载（反编译实锤）</h2>
 * <pre>
 *  9 参  blit(tex, x, y, u, v, w, h, texW, texH)        ← **不能缩放**：目标尺寸 = 源尺寸 = w×h
 * 11 参  blit(tex, x, y, dw, dh, u, v, srcW, srcH, texW, texH)  ← 可缩放：目标 dw×dh，源 srcW×srcH
 * </pre>
 * 9 参版本内部就是 `m_280411_(..., w, h, u, v, w, h, texW, texH)` —— 图片会按**原生像素** 1:1 画，
 * 大图直接冲出框外（1254px 图片画满屏幕）。要缩放必须用 11 参。
 *
 * <h2>现在</h2>
 * ①源尺寸只从**客户端纹理缓存**取（按 ResourceLocation 索引，键不可能对错），不再用 meta 里可能过期的值；
 * ②源尺寸未知 → **不画**（宁可不画，也不要显示左上角）；
 * ③等比缩放（contain）**整张完整显示**在图片框内，不裁剪；
 * ④每次绘制参数变化都打一行日志（可自证）。
 */
public final class TransportImage {
    private TransportImage() {}

    /** 上一次绘制的实际矩形（供日志自证）。 */
    public static int LAST_DX, LAST_DY, LAST_DW, LAST_DH;

    /**
     * 在 (x,y,w,h) 区域里**等比完整显示**整张图片（居中，多余处留空）。
     *
     * @param srcW/srcH 源图像素（<=0 = 未知 → 不画）
     */
    public static void drawInFrame(GuiGraphics g, ResourceLocation tex, int srcW, int srcH,
                                  int x, int y, int w, int h) {
        LAST_DX = LAST_DY = LAST_DW = LAST_DH = 0;
        if (tex == null || w <= 0 || h <= 0) return;
        if (srcW <= 0 || srcH <= 0) return;   // 尺寸未知宁可不画
        double s = Math.min(w / (double) srcW, h / (double) srcH);
        int dw = Math.max(1, (int) Math.round(srcW * s));
        int dh = Math.max(1, (int) Math.round(srcH * s));
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        LAST_DX = dx; LAST_DY = dy; LAST_DW = dw; LAST_DH = dh;
        // 241z-4：必须用 **11 参** blit（x,y,dw,dh, u,v,srcW,srcH,texW,texH 才能缩放）。
        // 之前用的 9 参 blit 在 1.20.1 里会把 width/height 同时当"目标尺寸"和"源尺寸"
        // → 图片按原生像素 1:1 画（1254px）→ 直接冲出框外/盖满屏幕。
        g.blit(tex, dx, dy, dw, dh, 0.0F, 0.0F, srcW, srcH, srcW, srcH);
    }

    public static String lastRect() {
        return "dx=" + LAST_DX + " dy=" + LAST_DY + " dw=" + LAST_DW + " dh=" + LAST_DH;
    }
}
