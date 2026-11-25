package top.cnpcplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import top.cnpcplus.mixin.GuiButtonOptionsAccess;

/** Adjacent vanilla buttons; the hidden native button remains the sole value/save handler. */
public final class GuiSelectionButton extends GuiButtonNop {
    private final GuiButtonNop original;
    private final int value;

    private GuiSelectionButton(IGuiInterface gui, GuiButtonNop original, int value, int x, int width, String label) {
        super(gui, 4000 + original.id * 4 + value, x, original.getY(), width, original.getHeight(), label, pressed -> {
            if (original.active && original.getValue() != value) {
                original.setDisplay(value);
                gui.buttonEvent(original);
            }
        });
        this.original = original;
        this.value = value;
        active = original.active;
    }

    public static void add(GuiBasic gui, GuiButtonNop original, int width) {
        String[] options = ((GuiButtonOptionsAccess)original).cnpcplus$options();
        original.visible = false;
        original.shown = false;
        for (int i = 0; i < options.length; i++) {
            int left = width * i / options.length, right = width * (i + 1) / options.length;
            GuiSelectionButton button = new GuiSelectionButton(gui, original, i, original.getX() + left, right - left, options[i]);
            gui.addButton(button);
        }
    }

    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        active = original.active;
        if (original.getValue() != value) {
            super.renderWidget(g, mouseX, mouseY, partial);
            return;
        }
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        // Inventory-slot greys: recessed pixel bevel, not the dark disabled-button texture.
        g.fill(x, y, x + w, y + h, active && isHoveredOrFocused() ? 0xff969696 : 0xff8b8b8b);
        g.hLine(x, x + w - 2, y, 0xff373737);
        g.vLine(x, y, y + h - 2, 0xff373737);
        g.hLine(x + 1, x + w - 2, y + 1, 0xff666666);
        g.vLine(x + 1, y + 1, y + h - 2, 0xff666666);
        g.hLine(x, x + w - 1, y + h - 1, 0xffffffff);
        g.vLine(x + w - 1, y, y + h - 1, 0xffffffff);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), w - 4);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2 + 1, active ? 0xffffffff : 0xffb0b0b0);
    }
}
