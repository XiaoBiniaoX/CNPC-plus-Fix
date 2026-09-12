package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.transform.GuiOutlinedLabel;
import bin.cnpcplus.transform.TransformEditModeStore;
import noppes.npcs.client.gui.advanced.GuiNPCNightSetup;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.DataTransform;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在「夜晚设置」界面挂一行当前编辑模式的标题。
 *
 * <h3>需求</h3>
 * 哈基彬：把编辑模式开启时，在夜晚设置的 UI 里显示当前是「日模式」还是「夜模式」，
 * 默认（编辑模式关）不出现。
 *
 * <h3>为什么需要额外记状态</h3>
 * {@code DataTransform} 里**没有**「当前编辑的是日还是夜」这个字段，
 * 只有 {@code editingModus}（是否处于编辑模式）与 {@code isActive}（是否处于变形态）。
 * 原版按钮 11 / 12（{@code GuiNPCNightSetup.java:91-95}）发的
 * {@code EnumPacketServer.TransformLoad} 是一次性动作包，参数 false = 日、true = 夜，
 * 服务端处理完不回传任何"当前在编辑哪套"的信息。
 *
 * 所以这里在客户端记住玩家最近点的是哪个按钮（{@link TransformEditModeStore}）。
 * 这与哈基彬的表述一致：「现在我配置的是夜间模式」指的就是他刚点过「载入夜晚」。
 *
 * <h3>为什么不用 isActive</h3>
 * {@code isActive} 表示 NPC 当前是否处于变形后的形态，是运行期状态，
 * 与「我正在编辑哪一套数据」是两件事。拿它当判据会在 NPC 恰好处于变形态时
 * 显示错误的标题。
 *
 * <h3>显示位置</h3>
 * 原版按钮 11「载入白天」在 {@code guiLeft+170, guiTop+34}，
 * 按钮 12「载入夜晚」在 {@code guiLeft+170, guiTop+56}（各 200x20，
 * {@code GuiNpcButton} 4 参构造走原版默认宽度 —— 阶段 26 的教训）。
 * 标题放在两个按钮下方 {@code guiTop+80}，与它们同一列左对齐，不与任何控件重叠。
 *
 * <h3>可读性（哈基彬第二轮反馈）</h3>
 * 首版用纯色 {@code GuiNpcLabel} 太黯淡 —— CNPC 底图是浅灰偏白的
 * （{@code GuiNPCInterface2.func_73863_a} 画的 menubg 系列贴图），
 * 亮色文字直接怼上去对比度很低。改用 {@link GuiOutlinedLabel}：
 * 半透明黑底衬块 + 四向黑描边 + 高亮度正文，不依赖具体贴图配色。
 *
 * <h3>为什么用 TAIL</h3>
 * 原版 {@code func_73866_w_} 第 58 行是 {@code if (!editingModus) return;}，
 * 编辑模式关闭时后两个按钮根本不 add。TAIL 注入在该 return 之后也会执行，
 * 所以标题的显示条件必须自己判 {@code editingModus}，不能依赖注入点。
 */
@Mixin(value = GuiNPCNightSetup.class, remap = false)
public class MixinGuiNPCNightSetupTitle {

    @Shadow private DataTransform data;

    /** 标题标签 id。原版用了 0..6 与 10，取 50 避免冲突。 */
    private static final int LABEL_ID = 50;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$addModeTitle(CallbackInfo ci) {
        if (this.data == null || !this.data.editingModus) return;

        // guiLeft / guiTop / addLabel 都在 GuiNPCInterface 上（不是 GuiNPCInterface2）。
        GuiNPCInterface base = (GuiNPCInterface) (Object) this;

        boolean night = TransformEditModeStore.isNight();
        // GuiNpcLabel 的构造器内部已经做了 I18n 翻译，这里必须传 lang 键本身，
        // 不能先翻译（否则会拿翻译结果去查表，查不到就原样显示键名）。
        String key = night
                ? "cnpcplus.transform.editingnight"
                : "cnpcplus.transform.editingday";
        // 用带黑底衬块 + 描边的标签（GuiOutlinedLabel）。
        // 哈基彬反馈：CNPC 底图是浅灰偏白的，纯彩色文字对比度太低看不清。
        // 颜色改用高亮度的暖黄 / 亮青，配黑底后可读性最好。
        base.addLabel(new GuiOutlinedLabel(LABEL_ID, key,
                base.guiLeft + 170, base.guiTop + 80,
                night ? 0x66D9FF : 0xFFD700));
    }

    /**
     * 跟随按钮 11 / 12 更新记录，并立刻重建界面让标题跟着变。
     *
     * 不 cancel：原版还要发 {@code TransformLoad} 包，那是真正的加载动作。
     * 用 TAIL 保证包已发出、原版逻辑完整执行后才刷新界面。
     */
    @Inject(method = "buttonEvent", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$trackMode(GuiButton guibutton, CallbackInfo ci) {
        if (guibutton == null) return;
        if (guibutton.id == 11) {
            TransformEditModeStore.setNight(false);
        } else if (guibutton.id == 12) {
            TransformEditModeStore.setNight(true);
        } else {
            return;
        }
        ((GuiNPCNightSetup) (Object) this).func_73866_w_();
    }
}
