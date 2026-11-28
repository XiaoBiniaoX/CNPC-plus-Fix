package bin.cnpcplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import bin.cnpcplus.mixin.advanced.IGuiButtonOptions;

/** A visible option segment that keeps the native button as its value and save target. */
public final class GuiChoiceButton extends GuiNpcButton {
    private final GuiNpcButton original;
    private final int value;

    private GuiChoiceButton(GuiNpcButton original, int value, int x, int y, int width, int height, String label) {
        super(30000 + original.id * 8 + value, x, y, width, height, label);
        this.original = original;
        this.value = value;
    }

    public GuiChoiceButton(int id, int x, int y, int width, int height, String label, boolean selected) {
        super(id, x, y, width, height, label);
        this.original = null;
        this.value = selected ? 1 : 0;
    }

    public static void replace(GuiNPCInterface gui, GuiNpcButton original, int width) {
        if (original == null) return;
        String[] labels = ((IGuiButtonOptions) original).cnpcplus$options();
        original.setVisible(false);
        for (int i = 0; i < labels.length; i++) {
            int left = width * i / labels.length, right = width * (i + 1) / labels.length;
            gui.addButton(new GuiChoiceButton(original, i, original.x + left, original.y, right - left,
                    original.height, labels[i]));
        }
    }

    public GuiNpcButton original() { return original; }
    public int value() { return value; }

    @Override public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (original != null) this.enabled = original.enabled;
        if (!visible) return;
        boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        if (original == null) {
            Gui.drawRect(x, y, x + width, y + height, value != 0 ? 0xFF555555 : 0xFF777777);
            Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1,
                    value != 0 ? 0xFFEEEEEE : hovered && enabled ? 0xFFD8D8D8 : 0xFFC0C0C0);
            String label = mc.fontRenderer.trimStringToWidth(displayString, width - 6);
            mc.fontRenderer.drawString(label, x + (width - mc.fontRenderer.getStringWidth(label)) / 2,
                    y + (height - 8) / 2 + 1, enabled ? 0xFF202020 : 0xFFB0B0B0, false);
            return;
        }
        if (original.getValue() != value) { super.drawButton(mc, mouseX, mouseY, partialTicks); return; }
        int background = hovered && enabled ? 0xFF969696 : 0xFF8B8B8B;
        Gui.drawRect(x, y, x + width, y + height, background);
        Gui.drawRect(x, y, x + width - 1, y + 1, 0xFF373737);
        Gui.drawRect(x, y, x + 1, y + height - 1, 0xFF373737);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 2, 0xFF666666);
        Gui.drawRect(x + 1, y + 1, x + 2, y + height - 1, 0xFF666666);
        Gui.drawRect(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
        Gui.drawRect(x + width - 1, y, x + width, y + height - 1, 0xFFFFFFFF);
        String label = mc.fontRenderer.trimStringToWidth(displayString, width - 6);
        int color = enabled ? 0xFFFFFFFF : 0xFFB0B0B0;
        mc.fontRenderer.drawString(label, x + (width - mc.fontRenderer.getStringWidth(label)) / 2,
                y + (height - 8) / 2 + 1, color, false);
    }
}
