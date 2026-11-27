package bin.cnpcplus.text.client;

import net.minecraft.client.gui.Gui;

/** A visible track and proportional thumb for existing row/page scrolling. */
public final class TextScrollbar {
    private TextScrollbar() { }

    public static void draw(int x, int y, int height, int offset, int visible, int total) {
        if (visible <= 0 || total <= visible || height <= 0) return;
        int thumb = Math.min(height, Math.max(8, (int) ((long) height * visible / total)));
        int top = y + (int) ((long) (height - thumb) * Math.max(0, Math.min(offset, total - visible)) / (total - visible));
        Gui.drawRect(x, y, x + 4, y + height, 0xCC202020);
        Gui.drawRect(x, top, x + 4, top + thumb, 0xFFD0D0D0);
        Gui.drawRect(x, top, x + 1, top + thumb, 0xFFF0F0F0);
    }
}
