package bin.cnpcplus.smelting.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import noppes.npcs.client.gui.util.GuiNpcButton;

/**
 * A 20x20 on/off button that shows an item icon instead of text, with a green
 * border when enabled and a red one when disabled.
 *
 * The two display strings are deliberately empty so the base class cannot draw
 * text over the icon when it cycles the value.
 */
public class GuiIconToggleButton extends GuiNpcButton {
    private static final int COLOR_ON = 0xFF00FF00;
    private static final int COLOR_OFF = 0xFFFF0000;
    private static final int COLOR_BG = 0xFF202020;

    private final ItemStack icon;
    private final String tooltipKey;

    public GuiIconToggleButton(int id, int x, int y, int width, int height,
                               ItemStack icon, boolean on, String tooltipKey) {
        super(id, x, y, width, height, new String[] {"", ""}, on ? 1 : 0);
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.tooltipKey = tooltipKey;
    }

    public String getTooltipKey() {
        return this.tooltipKey;
    }

    public boolean isOn() {
        return this.getValue() == 1;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        int border = this.getValue() == 1 ? COLOR_ON : COLOR_OFF;
        drawRect(this.x, this.y, this.x + this.width, this.y + this.height, border);
        drawRect(this.x + 1, this.y + 1, this.x + this.width - 1, this.y + this.height - 1, COLOR_BG);
        if (this.icon.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        // Lift the icon above the panel background so it cannot be covered.
        GlStateManager.translate(0.0F, 0.0F, 100.0F);
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(this.icon,
                this.x + (this.width - 16) / 2, this.y + (this.height - 16) / 2);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }
}
