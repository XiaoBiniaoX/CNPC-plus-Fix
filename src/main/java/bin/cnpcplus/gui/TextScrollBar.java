package bin.cnpcplus.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 只读文本区共用的细滚动条：绘制和行偏移计算均不保存状态。
 * 由各文本界面自己持有行起点，本类只做纯计算与绘制。
 */
public final class TextScrollBar {

    private TextScrollBar() {
    }

    /** 把行起点钳制到合法范围。 */
    public static int clamp(int row, int totalRows, int visibleRows) {
        return Math.max(0, Math.min(row, Math.max(0, totalRows - visibleRows)));
    }

    /** 按鼠标在滑轨上的位置换算行起点，用于点击滑条跳转。 */
    public static int rowForMouse(int mouseY, int top, int bottom, int totalRows, int visibleRows) {
        if (totalRows <= visibleRows || visibleRows <= 0 || bottom <= top) return 0;
        int track = bottom - top;
        int thumb = Math.max(10, track * visibleRows / totalRows);
        int travel = track - thumb;
        if (travel <= 0) return 0;
        int maxScroll = totalRows - visibleRows;
        return clamp((mouseY - top - thumb / 2) * maxScroll / travel, totalRows, visibleRows);
    }

    /** 内容未超出可视范围时不绘制，避免出现无意义的滑条。 */
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
