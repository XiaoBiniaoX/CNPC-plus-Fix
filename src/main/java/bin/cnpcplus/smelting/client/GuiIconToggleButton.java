package bin.cnpcplus.smelting.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

public class GuiIconToggleButton extends GuiButtonNop {
    public static final int COLOR_ON = 0xFF00FF00;
    public static final int COLOR_SOUND = 0xFF5555FF;
    private final ItemStack icon;
    private final Component tooltipText;
    private final int colorOn;
    public GuiIconToggleButton(IGuiInterface gui, int id, int x, int y, int width, int height,
                               ItemStack icon, boolean on, Component tooltip) {
        this(gui, id, x, y, width, height, icon, on, tooltip, COLOR_ON);
    }
    public GuiIconToggleButton(IGuiInterface gui, int id, int x, int y, int width, int height,
                               ItemStack icon, boolean on, Component tooltip, int colorOn) {
        super(gui, id, x, y, width, height, new String[]{"", ""}, on ? 1 : 0);
        this.icon = icon;
        this.tooltipText = tooltip;
        this.colorOn = colorOn;
    }
    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 对齐终版：父按钮图标/提示不置顶到子界面上。
        if (this.gui.hasSubGui()) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int border = getValue() == 1 ? this.colorOn : 0xFFFF0000;
        graphics.fill(x, y, x + w, y + h, 0xFF202020);
        graphics.hLine(x, x + w - 1, y, border); graphics.hLine(x, x + w - 1, y + h - 1, border);
        graphics.vLine(x, y, y + h - 1, border); graphics.vLine(x + w - 1, y, y + h - 1, border);
        if (!icon.isEmpty()) { graphics.pose().pushPose(); graphics.pose().translate(0, 0, 100); graphics.renderItem(icon, x + (w - 16) / 2, y + (h - 16) / 2); graphics.pose().popPose(); }
        if (this.tooltipText != null && isHovered()) {
            graphics.renderTooltip(Minecraft.getInstance().font, this.tooltipText, mouseX, mouseY);
        }
    }
}
