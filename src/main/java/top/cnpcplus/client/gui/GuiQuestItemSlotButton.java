package top.cnpcplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

/** An item-slot button with the same dark frame used by CNPC recipe editors. */
final class GuiQuestItemSlotButton extends GuiButtonNop {
    private static final ResourceLocation SLOT = new ResourceLocation("customnpcs", "textures/gui/slot.png");
    private final ItemStack icon;
    private final Component tooltip;

    GuiQuestItemSlotButton(IGuiInterface gui, int id, int x, int y, ItemStack icon, Component tooltip) {
        super(gui, id, x, y, 20, 20, " ");
        this.icon = icon;
        this.tooltip = tooltip;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        int x = getX(), y = getY();
        graphics.fill(x, y, x + 20, y + 20, 0xff171717);
        graphics.blit(SLOT, x + 1, y + 1, 0, 0, 18, 18);
        if (!icon.isEmpty()) graphics.renderItem(icon, x + 2, y + 2);
        if (isHovered()) {
            graphics.renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
            graphics.renderOutline(x, y, 20, 20, 0xfff4f0dc);
        }
    }
}
