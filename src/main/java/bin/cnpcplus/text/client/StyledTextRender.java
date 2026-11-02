package bin.cnpcplus.text.client;

import bin.cnpcplus.text.StyledText;
import bin.cnpcplus.text.TextSpans;
import java.nio.FloatBuffer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import noppes.npcs.client.ClientProxy;
import noppes.npcs.config.TrueTypeFont;
import org.lwjgl.BufferUtils;

/** Keep native font advance/selection metrics, adding only paint for RGB, bold, underline and italic. */
public final class StyledTextRender {
    private static final FloatBuffer SHEAR = BufferUtils.createFloatBuffer(16);
    static { SHEAR.put(new float[]{1,0,0,0,-.2f,1,0,0,0,0,1,0,0,0,0,1}).flip(); }
    private StyledTextRender() { }
    public static int width(Object font, String text) {
        if (font instanceof TrueTypeFont) return ((TrueTypeFont) font).width(text);
        if (font instanceof ClientProxy.FontContainer) return ((ClientProxy.FontContainer) font).width(text);
        return ((FontRenderer) font).getStringWidth(text);
    }
    private static void paint(Object font, String text, int x, int y, int color) {
        if (font instanceof TrueTypeFont) ((TrueTypeFont) font).draw(text, x, y, color);
        else if (font instanceof ClientProxy.FontContainer) ((ClientProxy.FontContainer) font).drawString(text, x, y, color);
        else ((FontRenderer) font).drawString(text, x, y, color);
    }
    public static void draw(Object font, StyledText value, int start, int end, int x, int y, int height, int fallback) {
        int[][] styles = TextSpans.read(value.spans, value.text.length());
        for (int from = start; from < end;) {
            int to = from + Character.charCount(value.text.codePointAt(from));
            while (to < end && styles[0][to] == styles[0][from] && styles[1][to] == styles[1][from])
                to += Character.charCount(value.text.codePointAt(to));
            String run = value.text.substring(from, to);
            int color = styles[0][from] < 0 ? fallback : (fallback & 0xFF000000) | styles[0][from], flags = styles[1][from];
            int left = width(font, value.text.substring(start, from));
            int right = width(font, value.text.substring(start, to));
            GlStateManager.pushMatrix(); GlStateManager.translate(x + left, y, 0);
            if ((flags & 4) != 0) { GlStateManager.translate(height * .2f, 0, 0); GlStateManager.multMatrix(SHEAR); }
            paint(font, run, 0, 0, color);
            if ((flags & 1) != 0) paint(font, run, 1, 0, color);
            if ((flags & 2) != 0) Gui.drawRect(0, height - 1, right - left, height, (color & 0xFF000000) == 0 ? 0xFF000000 | color : color);
            GlStateManager.popMatrix(); from = to;
        }
    }
}
