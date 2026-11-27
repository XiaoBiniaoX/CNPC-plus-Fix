package top.cnpcplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import top.cnpcplus.mixin.EditBoxScrollAccess;

/** Vanilla text/clipboard/keyboard handling, plus the drag selection missing in 1.20.1 EditBox. */
public final class NativeSearchField extends GuiTextFieldNop {
    public NativeSearchField(int id, Screen screen, int x, int y, int width, String text) {
        super(id, screen, x, y, width, 20, text);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !isFocused()) return false;
        int start = ((EditBoxScrollAccess)(Object)this).cnpcplus$displayPos();
        String text = getValue();
        var font = Minecraft.getInstance().font;
        int pixels = (int)Math.floor(x) - getX() - 4;
        int end;
        if (pixels < 0) end = text.offsetByCodePoints(start, start == 0 ? 0 : -1);
        else if (pixels > getInnerWidth()) {
            end = start + font.plainSubstrByWidth(text.substring(start), getInnerWidth()).length();
            if (end < text.length()) end = text.offsetByCodePoints(end, 1);
        } else end = start + font.plainSubstrByWidth(text.substring(start), pixels).length();
        if (end > 0 && end < text.length() && Character.isLowSurrogate(text.charAt(end))) end--;
        setHighlightPos(end); // Preserve the click anchor (cursorPos), including reverse dragging.
        return true;
    }
}
