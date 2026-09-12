package bin.cnpcplus.transform;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.util.GuiNpcLabel;

/**
 * 带黑底衬块 + 描边的标签，用于在 CNPC 灰白底图上显示醒目提示。
 *
 * <h3>为什么需要它（哈基彬反馈）</h3>
 * 「夜晚设置里面的日间/夜间 文本太黯淡了……CNPC 默认的 GUI 是灰色的（偏白），
 * 橙色和蓝色放上去不方便看」。
 * 原版 {@code GuiNpcLabel.drawLabel}（反编译 39-42 行）只有一行
 * {@code fontRenderer.drawString(label, x, y, color)} —— 无阴影、无描边、无背景。
 * 而 {@code GuiNPCInterface2.func_73863_a} 画的底图是浅灰的 {@code menubg} 系列贴图，
 * 亮色文字直接怼上去对比度很低。
 *
 * <h3>做法</h3>
 * 先画一块半透明黑色衬底（局部黑框），再用四向偏移画黑色描边，最后画彩色正文。
 * 这样无论底图是浅灰还是深色都能看清，也不依赖具体贴图。
 *
 * <h3>为什么用子类而不是混入 GuiNpcLabel</h3>
 * {@code drawLabel(GuiScreen, FontRenderer)} 是 public 且由
 * {@code GuiNPCInterface.func_73863_a} 的标签循环（反编译 366-368 行）调用，
 * 直接继承重写即可，无需注入。混入 {@code GuiNpcLabel} 会影响 CNPC 全部界面的
 * 所有标签 —— 那是画蛇添足，也违反最小改动。
 *
 * <h3>纯客户端</h3>
 * 本类只被 client 侧混入 {@code MixinGuiNPCNightSetupTitle} 引用，
 * 服务端不可达。放在 mixin 包外（阶段 22 的 {@code IllegalClassLoadError} 教训）。
 */
public class GuiOutlinedLabel extends GuiNpcLabel {

    /** 衬底相对文字的内边距。 */
    private static final int PAD_X = 3;
    private static final int PAD_TOP = 2;
    private static final int PAD_BOTTOM = 2;

    /** 半透明黑底。ARGB，0xC0 约 75% 不透明，压住底图又不显得死黑。 */
    private static final int BACKDROP = 0xC0000000;

    /** 描边色。纯黑不透明。 */
    private static final int OUTLINE = 0xFF000000;

    public GuiOutlinedLabel(int id, Object label, int x, int y, int color) {
        super(id, label, x, y, color);
    }

    @Override
    public void drawLabel(GuiScreen gui, FontRenderer fontRenderer) {
        if (!this.enabled || this.label == null) return;

        int width = fontRenderer.getStringWidth(this.label);
        int height = fontRenderer.FONT_HEIGHT;

        // 局部黑框：把文字所在矩形铺一层半透明黑，解决浅灰底图上的对比度问题。
        Gui.drawRect(this.x - PAD_X, this.y - PAD_TOP,
                this.x + width + PAD_X, this.y + height + PAD_BOTTOM - 1,
                BACKDROP);

        // 四向描边。只画上下左右不画对角：1px 字体上四向已经足够形成轮廓，
        // 八向会让小字号显得糊。
        fontRenderer.drawString(this.label, this.x - 1, this.y, OUTLINE);
        fontRenderer.drawString(this.label, this.x + 1, this.y, OUTLINE);
        fontRenderer.drawString(this.label, this.x, this.y - 1, OUTLINE);
        fontRenderer.drawString(this.label, this.x, this.y + 1, OUTLINE);

        // 正文压在最后，保证描边不盖住它。
        fontRenderer.drawString(this.label, this.x, this.y, this.color);
    }
}
