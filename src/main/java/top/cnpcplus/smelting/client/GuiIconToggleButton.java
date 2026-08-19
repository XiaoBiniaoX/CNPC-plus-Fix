package top.cnpcplus.smelting.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

/**
 * 图标开关按钮：按钮面只画一个物品图标 + 一圈高亮边框（开=绿框，关=红框），不画任何文字。
 * 覆盖 renderWidget（而非 render）——原版 Button.renderWidget 会画底图与居中文字，覆盖掉即不画文字；
 * 同时保留 vanilla AbstractWidget.render 的悬停判定与 tooltip 机制（鼠标悬停显示这个开关是干嘛的）。
 * 点击仍走 GuiButtonNop 的 display 轮换 + gui.buttonEvent。
 */
public class GuiIconToggleButton extends GuiButtonNop {

    private static final int COLOR_ON = 0xFF00FF00;   // 绿框（启用）
    private static final int COLOR_OFF = 0xFFFF0000;  // 红框（禁用，默认）
    private static final int COLOR_BG = 0xFF202020;   // 按钮底色

    private final ItemStack icon;

    public GuiIconToggleButton(IGuiInterface gui, int id, int x, int y, int width, int height,
                               ItemStack icon, boolean on, Component tooltip) {
        // display 两项都是空串：即使父类逻辑改了显示文本，也不会画出任何字
        super(gui, id, x, y, width, height, new String[]{"", ""}, on ? 1 : 0);
        this.icon = icon;
        if (tooltip != null) this.setTooltip(Tooltip.create(tooltip));
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        int border = this.getValue() == 1 ? COLOR_ON : COLOR_OFF;

        // 底色 + 四边高亮框（红=禁用 / 绿=启用）
        graphics.fill(x, y, x + w, y + h, COLOR_BG);
        graphics.hLine(x, x + w - 1, y, border);
        graphics.hLine(x, x + w - 1, y + h - 1, border);
        graphics.vLine(x, y, y + h - 1, border);
        graphics.vLine(x + w - 1, y, y + h - 1, border);

        // 物品图标（16x16）居中；抬高 z 防止被背景板盖住
        if (!this.icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 100.0f);
            graphics.renderItem(this.icon, x + (w - 16) / 2, y + (h - 16) / 2);
            graphics.pose().popPose();
        }
    }
}
