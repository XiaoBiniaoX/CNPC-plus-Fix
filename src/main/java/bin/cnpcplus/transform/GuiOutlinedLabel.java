package bin.cnpcplus.transform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * 带半透明黑底衬块 + 四向描边的标签，用于在 CNPC 灰白底图上显示醒目提示。
 *
 * <p>为什么需要它（哈基彬在 1.12.2 那边的反馈）：CNPC 的底图是浅灰偏白的 menubg 系列贴图，
 * 而原版 {@code GuiLabel.renderWidget} 只有一行 {@code drawString}，无阴影无描边无背景，
 * 亮色文字直接怼上去对比度很低，橙色和蓝色都不方便看。
 *
 * <p>做法：先铺一块半透明黑衬底，再用四向偏移画黑描边，最后画彩色正文。
 * 这样无论底图是浅灰还是深色都能看清，也不依赖具体贴图配色。
 * 只画上下左右不画对角：1px 字体四向已足够形成轮廓，八向会让小字号显得糊。
 *
 * <p>用子类而不是混入 {@code GuiLabel}：后者会影响 CNPC 全部界面的所有标签，
 * 属画蛇添足且违反最小改动。{@code renderWidget} 是可见方法，继承重写即可。
 *
 * <p>纯客户端，只被 client 段混入引用。放在 mixin 包外避免 IllegalClassLoadError。
 */
public class GuiOutlinedLabel extends GuiLabel {

    /** 衬底相对文字的内边距。 */
    private static final int PAD_X = 3;
    private static final int PAD_TOP = 2;
    private static final int PAD_BOTTOM = 2;

    /** 半透明黑底。ARGB，0xC0 约 75% 不透明，压住底图又不显得死黑。 */
    private static final int BACKDROP = 0xC0000000;

    /** 描边色。纯黑不透明。 */
    private static final int OUTLINE = 0xFF000000;

    private final String cnpcplus$text;
    private final int cnpcplus$color;

    /**
     * @param translationKey 语言键。这里自己翻译而不是交给父类，
     *                       因为绘制时要按最终文本宽度算衬底矩形。
     */
    public GuiOutlinedLabel(int id, String translationKey, int x, int y, int color) {
        super(id, translationKey, x, y, color);
        this.cnpcplus$text = I18n.get(translationKey);
        this.cnpcplus$color = color;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.enabled) return;
        if (this.cnpcplus$text == null || this.cnpcplus$text.isEmpty()) return;

        var font = Minecraft.getInstance().font;
        int x = this.getX();
        int y = this.getY();
        int w = font.width(this.cnpcplus$text);
        int h = font.lineHeight;

        // 局部黑框：解决浅灰底图上的对比度问题。
        graphics.fill(x - PAD_X, y - PAD_TOP, x + w + PAD_X, y + h + PAD_BOTTOM - 1, BACKDROP);

        // 四向描边。
        graphics.drawString(font, this.cnpcplus$text, x - 1, y, OUTLINE, false);
        graphics.drawString(font, this.cnpcplus$text, x + 1, y, OUTLINE, false);
        graphics.drawString(font, this.cnpcplus$text, x, y - 1, OUTLINE, false);
        graphics.drawString(font, this.cnpcplus$text, x, y + 1, OUTLINE, false);

        // 正文压在最后，保证描边不盖住它。
        graphics.drawString(font, this.cnpcplus$text, x, y, this.cnpcplus$color, false);
    }
}
