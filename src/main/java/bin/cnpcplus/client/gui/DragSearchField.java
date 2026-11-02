package bin.cnpcplus.client.gui;

import bin.cnpcplus.mixin.gui.ITextFieldCursor;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import org.lwjgl.input.Mouse;

/** Native MC selection/clipboard, extended with pointer drag (including scrolled long text). */
public final class DragSearchField extends GuiNpcTextField {
    private boolean dragging;
    private final int boxWidth, boxHeight;
    public DragSearchField(int id, GuiScreen parent, int x, int y, int width, int height, String text) {
        super(id, parent, x, y, width, height, text); boxWidth = width; boxHeight = height;
    }
    @Override public boolean mouseClicked(int mouseX, int mouseY, int button) {
        boolean clicked = super.mouseClicked(mouseX, mouseY, button);
        dragging = button == 0 && isFocused() && mouseX >= x && mouseX < x + boxWidth && mouseY >= y && mouseY < y + boxHeight;
        return clicked;
    }
    @Override public void drawTextBox(int mouseX, int mouseY) {
        if (dragging && (!isFocused() || !Mouse.isButtonDown(0))) dragging = false;
        if (dragging) {
            int anchor = getCursorPosition();
            if (mouseX < x + 4) setSelectionPos(getSelectionEnd() - 1);
            else if (mouseX >= x + boxWidth - 4) setSelectionPos(getSelectionEnd() + 1);
            else {
                super.mouseClicked(mouseX, y + boxHeight / 2, 0);
                int end = getCursorPosition();
                ((ITextFieldCursor) (Object) this).cnpcplus$anchor(anchor); setSelectionPos(end);
            }
        }
        super.drawTextBox(mouseX, mouseY);
    }
}
