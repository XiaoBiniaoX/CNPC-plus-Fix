package top.cnpcplus.transform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * 带半透明黑底衬块 + 四向描边的标签，用于在 CNPC 灰白底图上显示醒目提示。
 *
 * <h3>为什么需要它（哈基彬在 1.12.2 上的第二轮反馈）</h3>
 * 「夜晚设置里面的日间/夜间文本太黯淡了……CNPC 默认的 GUI 是灰色的（偏白），
 * 橙色和蓝色放上去不方便看」。原版 {@code GuiLabel.m_88315_}（反编译 80-90 行）
 * 正文只有一次 {@code graphics.m_280614_(..., false)} —— 最后那个 false 就是
 * {@code dropShadow=false}，即**连阴影都没有**，纯色文字直接怼在浅灰 menubg 上对比度很低。
 *
 * <h3>为什么不用原版自带的 labelBgEnabled</h3>
 * {@code GuiLabel} 其实有一套背景框机制（{@code drawBox}，反编译 98-109 行），
 * 但 {@code labelBgEnabled} / {@code backColor} / {@code border} 全是 private
 * 且没有任何 setter，构造器也不碰它们 —— 那套机制在这个版本里对外部完全不可用。
 * 所以只能自己画。
 *
 * <h3>做法</h3>
 * 半透明黑衬底 → 四向黑描边 → 彩色正文。无论底图是浅灰还是深色都能看清，
 * 也不依赖具体贴图配色。
 *
 * <h3>为什么用子类而不是混入 GuiLabel</h3>
 * {@code m_88315_} 是 public 且由 {@code GuiBasic.m_88315_} 的标签循环
 * （反编译 294-296 行）逐个调用，直接继承重写即可。
 * 混入 {@code GuiLabel} 会影响 CNPC 全部界面的所有标签，那是画蛇添足。
 *
 * <h3>纯客户端 + 放在 mixin 包外</h3>
 * 只被 client 侧的 {@code MixinGuiNPCNightSetupTitle} 引用，服务端不可达。
 * 刻意放在 mixin 包外：mixin 包内的类不能被外部直接引用（{@code IllegalClassLoadError}）。
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

    private final int cnpcplusColor;

    public GuiOutlinedLabel(int id, String langKey, int x, int y, int color) {
        super(id, langKey, x, y, color);
        // 父类的 textColor 是 private，重写 render 后拿不到，自己留一份。
        this.cnpcplusColor = color;
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.enabled) return;

        Font font = Minecraft.getInstance().font;
        Component text = this.getMessage();
        int x = this.getX();
        int y = this.getY();
        int w = font.width(text);
        int h = font.lineHeight;

        // 局部黑框：把文字所在矩形铺一层半透明黑，解决浅灰底图上的对比度问题。
        graphics.fill(x - PAD_X, y - PAD_TOP, x + w + PAD_X, y + h + PAD_BOTTOM - 1, BACKDROP);

        // 四向描边。只画上下左右不画对角：1px 字体上四向已足够形成轮廓，八向会让小字号显得糊。
        graphics.drawString(font, text, x - 1, y, OUTLINE, false);
        graphics.drawString(font, text, x + 1, y, OUTLINE, false);
        graphics.drawString(font, text, x, y - 1, OUTLINE, false);
        graphics.drawString(font, text, x, y + 1, OUTLINE, false);

        // 正文压在最后，保证描边不盖住它。
        graphics.drawString(font, text, x, y, this.cnpcplusColor, false);
    }
}
