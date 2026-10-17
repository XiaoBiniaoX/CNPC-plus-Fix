package top.cnpcplus.smelting.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

/**
 * 图标开关按钮：按钮面只画一个物品图标 + 一圈高亮边框（开=绿框，关=红框），不画任何文字。
 * 覆盖 renderWidget（而非 render）——原版 Button.renderWidget 会画底图与居中文字，覆盖掉即不画文字；
 * 点击仍走 GuiButtonNop 的 display 轮换 + gui.buttonEvent。
 *
 * <h3>tooltip 自绘（233d-UI4 修复「仅名字检查置顶到 subgui 上」）</h3>
 * 旧实现用 vanilla {@code setTooltip}——tooltip 由
 * {@code Screen.renderWithTooltip} 在 {@code render}（含 subgui z+60 渲染）
 * <b>之后</b>统一画 hovered widget 的 tooltip，导致 subgui 打开时父按钮
 * tooltip 盖在 subgui 之上。现删 vanilla setTooltip，改 renderWidget 尾部
 * 自绘：{@code isHovered() && !gui.hasSubGui()} 双条件，subgui 存在时
 * 永不画（hasSubGui 时父组件收到的渲染坐标本来也是 0,0，双保险）。
 */
public class GuiIconToggleButton extends GuiButtonNop {

    /** 绿框（启用——233d-UI6 起改为可配置「on 色」的默认值）。 */
    public static final int COLOR_ON = 0xFF00FF00;
    /** 蓝框（233d-UI6 音效专用「已设置」态：与开/关语义区分）。 */
    public static final int COLOR_SOUND = 0xFF5555FF;
    private static final int COLOR_OFF = 0xFFFF0000;  // 红框（禁用，默认）
    private static final int COLOR_BG = 0xFF202020;   // 按钮底色

    private final ItemStack icon;
    private final Component tooltipText;
    /** on=true 时的边框色（默认绿；音效钮传蓝=「已绑定」专属态）。 */
    private final int colorOn;

    public GuiIconToggleButton(IGuiInterface gui, int id, int x, int y, int width, int height,
                               ItemStack icon, boolean on, Component tooltip) {
        this(gui, id, x, y, width, height, icon, on, tooltip, COLOR_ON);
    }

    /** 233d-UI6：允许指定 on 态边框色（音效钮=蓝框「已设置」，其余仍默认绿）。 */
    public GuiIconToggleButton(IGuiInterface gui, int id, int x, int y, int width, int height,
                               ItemStack icon, boolean on, Component tooltip, int colorOn) {
        // display 两项都是空串：即使父类逻辑改了显示文本，也不会画出任何字
        super(gui, id, x, y, width, height, new String[]{"", ""}, on ? 1 : 0);
        this.icon = icon;
        this.tooltipText = tooltip; // 不用 vanilla setTooltip（见类注释），renderWidget 自绘
        this.colorOn = colorOn;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // subgui 打开时整个按钮不画（233d-UI5：icon 曾用 translate z+100 反超
        // subgui 的 z+60「置顶」在粒子编辑面板上；tooltip 已拦、icon 同样拦。
        // 父界面按钮在 subgui 下无视觉意义，关闭 subgui 后自动恢复）
        if (this.gui.hasSubGui()) {
            return;
        }
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        int border = this.getValue() == 1 ? this.colorOn : COLOR_OFF;

        // 底色 + 四边高亮框（红=禁用 / 绿=启用 / 蓝=音效已设置）
        graphics.fill(x, y, x + w, y + h, COLOR_BG);
        graphics.hLine(x, x + w - 1, y, border);
        graphics.hLine(x, x + w - 1, y + h - 1, border);
        graphics.vLine(x, y, y + h - 1, border);
        graphics.vLine(x + w - 1, y, y + h - 1, border);

        // 物品图标（16x16）居中；抬高 z 防止被背景板盖住（低于 subgui z+60）
        if (!this.icon.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 100.0f);
            graphics.renderItem(this.icon, x + (w - 16) / 2, y + (h - 16) / 2);
            graphics.pose().popPose();
        }

        // 悬停提示（自绘；subgui 打开时不画，防盖住子界面）
        if (this.tooltipText != null && this.isHovered() && !this.gui.hasSubGui()) {
            graphics.renderTooltip(Minecraft.getInstance().font,
                    this.tooltipText, mouseX, mouseY);
        }
    }
}
