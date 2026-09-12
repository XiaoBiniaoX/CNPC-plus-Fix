package top.cnpcplus.mixin;

import noppes.npcs.client.gui.advanced.GuiNPCNightSetup;
import noppes.npcs.controllers.data.DataTransform;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transform.GuiOutlinedLabel;
import top.cnpcplus.transform.TransformEditModeStore;

/**
 * 在「夜晚设置」界面挂一行当前编辑模式的标题（哈基彬需求 A2）。
 *
 * <h3>需求</h3>
 * 「给日间模式和夜间模式的配置设置添加一个醒目的标题（比如现在我配置的是夜间模式，
 * 就出现一行小字，默认不出现）」。
 *
 * <h3>为什么需要额外记状态</h3>
 * {@code DataTransform} 里**没有**「当前编辑的是日还是夜」这个字段，
 * 只有 {@code editingModus}（是否处于编辑模式）与 {@code isActive}（是否处于变形态）。
 * 原版按钮 11/12（反编译 {@code GuiNPCNightSetup.java:97-101}）发的
 * {@code SPacketNpcTransform} 是一次性动作包（参数 false=日、true=夜），
 * 服务端 {@code DataTransform.transform} 做完两套数据互换就结束，不回传任何状态。
 *
 * <p>所以在客户端记住玩家最近点的是哪个按钮（{@link TransformEditModeStore}）。
 * 这与哈基彬的表述一致：「现在我配置的是夜间模式」指的就是他刚点过「载入夜晚」。
 *
 * <h3>为什么不用 isActive</h3>
 * {@code isActive} 表示 NPC 当前是否处于变形后的形态，是运行期状态，
 * 与「我正在编辑哪一套数据」是两件事。拿它当判据会在 NPC 恰好处于变形态时显示错误标题。
 *
 * <h3>显示位置与配色（沿用 1.12.2 已定稿的值）</h3>
 * 原版按钮 11「载入白天」在 {@code guiLeft+170, guiTop+34}，
 * 按钮 12「载入夜晚」在 {@code guiLeft+170, guiTop+56}。
 * 标题放两个按钮下方 {@code guiTop+80}，同列左对齐，原版这一列到 +56 就结束，不重叠。
 *
 * <p>配色与带底描边的做法都来自哈基彬在 1.12.2 上的第二轮反馈：纯色标签在
 * CNPC 的浅灰 menubg 上太黯淡，改用 {@link GuiOutlinedLabel}，
 * 夜=亮青 {@code 0x66D9FF} / 日=金 {@code 0xFFD700}。
 *
 * <h3>为什么用 TAIL</h3>
 * 原版 {@code m_7856_} 第 64 行是 {@code if (!this.data.editingModus) return;}，
 * 编辑模式关闭时后两个按钮根本不 add。TAIL 注入在该 return 之后**也会**执行，
 * 所以显示条件必须自己判 {@code editingModus}，不能依赖注入点 ——
 * 这正好对应需求里的「默认不出现」。
 */
@Mixin(value = GuiNPCNightSetup.class, remap = false)
public class MixinGuiNPCNightSetupTitle {

    @Shadow(remap = false)
    private DataTransform data;

    /** 标题标签 id。原版用了 0..6 与 10，取 50 避免冲突。 */
    private static final int LABEL_ID = 50;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addModeTitle(CallbackInfo ci) {
        if (this.data == null || !this.data.editingModus) return;

        GuiNPCNightSetup self = (GuiNPCNightSetup) (Object) this;
        boolean night = TransformEditModeStore.isNight();
        // GuiLabel 的 String 构造器内部走 Component.translatable，所以这里必须传
        // lang 键本身，不能先翻译（否则会拿翻译结果去查表，查不到就原样显示键名）。
        String key = night
                ? "cnpcplus.transform.editingnight"
                : "cnpcplus.transform.editingday";
        self.addLabel(new GuiOutlinedLabel(LABEL_ID, key,
                self.guiLeft + 170, self.guiTop + 80,
                night ? 0x66D9FF : 0xFFD700));
    }

    /**
     * 跟随按钮 11/12 更新记录，并立刻重建界面让标题跟着变。
     *
     * <p>不 cancel：原版还要发 {@code SPacketNpcTransform}，那是真正的加载动作。
     * 用 TAIL 保证包已发出、原版逻辑完整执行后才刷新界面。
     */
    @Inject(method = "buttonEvent", at = @At("TAIL"), remap = false)
    private void cnpcplus$trackMode(GuiButtonNop button, CallbackInfo ci) {
        if (button == null) return;
        if (button.id == 11) {
            TransformEditModeStore.setNight(false);
        } else if (button.id == 12) {
            TransformEditModeStore.setNight(true);
        } else {
            return;
        }
        ((GuiNPCNightSetup) (Object) this).m_7856_();
    }
}
