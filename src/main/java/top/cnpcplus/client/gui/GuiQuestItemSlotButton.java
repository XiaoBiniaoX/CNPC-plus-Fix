package top.cnpcplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

/** An item-slot button with the same dark frame used by CNPC recipe editors. */
final class GuiQuestItemSlotButton extends GuiButtonNop {
    private ItemStack icon;
    private final Component tooltip;
    private boolean selected;

    GuiQuestItemSlotButton(IGuiInterface gui, int id, int x, int y, ItemStack icon, Component tooltip) {
        super(gui, id, x, y, 20, 20, " ");
        this.icon = icon;
        this.tooltip = tooltip;
    }

    void setItem(ItemStack item) { icon = item; }
    void setSelected(boolean value) { selected = value; }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        int x = getX(), y = getY();
        graphics.fill(x, y, x + 20, y + 20, 0xff373737);
        graphics.fill(x + 1, y + 1, x + 20, y + 20, 0xffffffff);
        graphics.fill(x + 1, y + 1, x + 19, y + 19, isHoveredOrFocused() ? 0xffa0a0a0 : 0xff8b8b8b);
        if (!icon.isEmpty()) graphics.renderItem(icon, x + 2, y + 2);
        if (selected || isFocused()) graphics.renderOutline(x - 1, y - 1, 22, 22, 0xfff4f0dc);
        if (isHovered()) {
            if (icon.isEmpty() && tooltip != null) graphics.renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
            else graphics.renderTooltip(Minecraft.getInstance().font, icon, mouseX, mouseY);
            graphics.renderOutline(x, y, 20, 20, 0xfff4f0dc);
        }
    }
}
