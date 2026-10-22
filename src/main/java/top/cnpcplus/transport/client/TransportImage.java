package top.cnpcplus.transport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 任务241z：传送点图片绘制（玩家面板 + 编辑页共用）——**重写版**。
 *
 * <h2>为什么重写</h2>
 * 之前一直"裁剪 / 只显示左上角"的真正原因不是数学，而是 **blit 的参数顺序用反了**：
 * 1.20.1 的签名是
 * <pre>
 *   blit(ResourceLocation tex, int destX, int destY,
 *        float uOffset, float vOffset,
 *        int srcWidth, int srcHeight,      ← 源图尺寸（texel）
 *        int destWidth, int destHeight)    ← 目标尺寸（像素）
 * </pre>
 * 之前写成了 {@code blit(tex, dx, dy, 0, 0, dw, dh, srcW, srcH)}（源/目标颠倒）
 * → 实际采样的是贴图左上角 (dw×dh) 那一小块，再放大到 srcW×srcH → **永远只看到左上角放大图**。
 * 图标/面板/边框之所以一直正常，是因为它们源尺寸 = 目标尺寸（1:1），把这个错误掩盖了。
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
        // 241z：源尺寸(srcW,srcH) 在前，目标尺寸(dw,dh) 在后 —— 顺序千万别再弄反
        g.blit(tex, dx, dy, 0.0F, 0.0F, srcW, srcH, dw, dh);
    }

    public static String lastRect() {
        return "dx=" + LAST_DX + " dy=" + LAST_DY + " dw=" + LAST_DW + " dh=" + LAST_DH;
    }
}
