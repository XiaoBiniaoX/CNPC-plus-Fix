package top.cnpcplus.gui;

import net.minecraft.client.gui.GuiGraphics;

/** 只读文本区共用的细滚动条：绘制和行偏移计算均不保存状态。 */
public final class TextScrollBar {
    private TextScrollBar() {
    }

    public static int clamp(int row, int totalRows, int visibleRows) {
        return Math.max(0, Math.min(row, Math.max(0, totalRows - visibleRows)));
    }

    public static int rowForMouse(int mouseY, int top, int bottom,
                                  int totalRows, int visibleRows) {
        if (totalRows <= visibleRows || visibleRows <= 0 || bottom <= top) return 0;
        int track = bottom - top;
        int thumb = Math.max(10, track * visibleRows / totalRows);
        int travel = track - thumb;
        if (travel <= 0) return 0;
        int maxScroll = totalRows - visibleRows;
        return clamp((mouseY - top - thumb / 2) * maxScroll / travel, totalRows, visibleRows);
    }

    public static void draw(GuiGraphics graphics, int x, int top, int bottom,
                            int totalRows, int visibleRows, int rowStart) {
        if (totalRows <= visibleRows || visibleRows <= 0 || bottom <= top) return;
        int track = bottom - top;
        int maxScroll = totalRows - visibleRows;
        int thumb = Math.max(10, track * visibleRows / totalRows);
        int thumbTop = top + (track - thumb) * clamp(rowStart, totalRows, visibleRows) / maxScroll;
        graphics.fill(x, top, x + 2, bottom, 0x55000000);
        graphics.fill(x, thumbTop, x + 2, thumbTop + thumb, 0xD0D0D0D0);
    }
}
