package bin.cnpcplus.mixin.transform;

import bin.cnpcplus.transform.GuiOutlinedLabel;
import bin.cnpcplus.transform.TransformEditModeStore;
import noppes.npcs.client.gui.advanced.GuiNPCNightSetup;
import noppes.npcs.controllers.data.DataTransform;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在「夜间设置」界面显示一行当前编辑模式的醒目标题。
 *
 * <p>与 1.12.2 的实现对齐（哈基彬要求以 1.12.2 的 UI 为准）：
 * 判据用 {@code editingModus} + 客户端记住最近点的是按钮 11（白天）还是 12（夜间），
 * 而不是 {@code isActive}。理由见 {@link TransformEditModeStore} 的说明 ——
 * {@code isActive} 是「NPC 当前是否处于变形态」这个运行期状态，
 * 与「我正在编辑哪一套数据」是两件事。默认（编辑模式关）不显示任何东西。
 *
 * <p>位置照 1.12.2：原版按钮 11「加载白天设置」在 {@code guiLeft+170, guiTop+34}、
 * 按钮 12「加载夜间设置」在 {@code guiLeft+170, guiTop+56}（{@code GuiNPCNightSetup:67-68}），
 * 标题放在两者下方 {@code guiTop+80}，同列左对齐，不与任何控件重叠。
 *
 * <p>用 TAIL 注入：原版 {@code init} 第 66 行是 {@code if (!data.editingModus) return;}，
 * 编辑模式关闭时后两个按钮根本不 add。TAIL 在该 return 之后也会执行，
 * 所以显示条件必须自己判 {@code editingModus}，不能依赖注入点。
 */
@Mixin(value = GuiNPCNightSetup.class, remap = false)
public class MixinGuiNPCNightSetupModeLabel {

    @Shadow
    private DataTransform data;

    /** 标题标签 id。原版用了 0..6 与 10，取 50 避免冲突。 */
    private static final int CNPCPLUS_LABEL_ID = 50;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$addModeTitle(CallbackInfo ci) {
        if (this.data == null || !this.data.editingModus) return;

        GuiNPCNightSetup self = (GuiNPCNightSetup) (Object) this;
        boolean night = TransformEditModeStore.isNight();
        String key = night
                ? "cnpcplus.transform.editingnight"
                : "cnpcplus.transform.editingday";
        // 颜色用高亮度的亮青（夜）/ 暖黄（日），配黑底衬块后在灰白底图上可读性最好。
        self.addLabel(new GuiOutlinedLabel(CNPCPLUS_LABEL_ID, key,
                self.guiLeft + 170, self.guiTop + 80,
                night ? 0x66D9FF : 0xFFD700));
    }

    /**
     * 跟随按钮 11/12 更新记录，并立刻重建界面让标题跟着变。
     *
     * <p>不 cancel：原版还要发 {@code SPacketNpcTransform}，那是真正的加载动作。
     * 用 TAIL 保证包已发出、原版逻辑完整执行后才刷新界面。
     */
    @Inject(method = "buttonEvent", at = @At("TAIL"))
    private void cnpcplus$trackMode(GuiButtonNop guibutton, CallbackInfo ci) {
        if (guibutton == null) return;
        if (guibutton.id == 11) {
            TransformEditModeStore.setNight(false);
        } else if (guibutton.id == 12) {
            TransformEditModeStore.setNight(true);
        } else {
            return;
        }
        ((GuiNPCNightSetup) (Object) this).init();
    }
}
