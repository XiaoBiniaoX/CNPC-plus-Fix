package bin.cnpcplus.smelting.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

public class GuiIconToggleButton extends GuiButtonNop {
    private final ItemStack icon;
    public GuiIconToggleButton(IGuiInterface gui, int id, int x, int y, int width, int height,
                               ItemStack icon, boolean on, Component tooltip) {
        super(gui, id, x, y, width, height, new String[]{"", ""}, on ? 1 : 0);
        this.icon = icon;
        if (tooltip != null) setTooltip(Tooltip.create(tooltip));
    }
    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int border = getValue() == 1 ? 0xFF00FF00 : 0xFFFF0000;
        graphics.fill(x, y, x + w, y + h, 0xFF202020);
        graphics.hLine(x, x + w - 1, y, border); graphics.hLine(x, x + w - 1, y + h - 1, border);
        graphics.vLine(x, y, y + h - 1, border); graphics.vLine(x + w - 1, y, y + h - 1, border);
        if (!icon.isEmpty()) { graphics.pose().pushPose(); graphics.pose().translate(0, 0, 100); graphics.renderItem(icon, x + (w - 16) / 2, y + (h - 16) / 2); graphics.pose().popPose(); }
    }
}
