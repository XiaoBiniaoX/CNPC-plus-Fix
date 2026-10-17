package top.cnpcplus.mixin;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.gui.SubGuiNpcRespawn;
import noppes.npcs.entity.data.DataStats;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.client.SubGuiDropFx;
import top.cnpcplus.stats.DeathFxStore;
import top.cnpcplus.stats.HideDelayStore;

import java.util.List;

/**
 * 238-2/3：重生界面加「尸体隐藏延迟」输入框与「死亡特效」按钮。
 *
 * <h3>布局（原版 y30-94 控件 + 底部 done y190，y100-185 空档）</h3>
 * y110：标签+延迟输入框（id 8，x 与原时间框对齐 122）；
 * y135：标签+特效按钮（id 60）。id 规避原版 0/2/4/66。
 *
 * <h3>数据</h3>
 * 延迟写 {@link HideDelayStore}（NBT 随 STATS 包同步，缺省 5）；
 * 特效复用 {@code SubGuiDropFx}（Consumer 回写 {@link DeathFxStore}）。
 * 按钮文案数字先 {@code I18n} 再拼（同 234 先例）。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = SubGuiNpcRespawn.class, remap = false)
public abstract class MixinSubGuiNpcRespawnExtras implements ITextfieldListener {

    @Shadow(remap = false)
    private DataStats stats;

    @Unique
    private static final int TF_DELAY = 8;

    @Unique
    private static final int BTN_FX = 60;

    @Unique
    private GuiTextFieldNop cnpcplus$delayTf;

    @Unique
    private GuiLabel cnpcplus$delayLabel;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addDelayAndFx(CallbackInfo ci) {
        SubGuiNpcRespawn self = (SubGuiNpcRespawn) (Object) this;
        // 238c（#2）：延迟框随「隐藏尸体」开关显隐——恒建+enabled，buttonEvent TAIL 翻转。
        boolean hideOn = this.stats.hideKilledBody;
        this.cnpcplus$delayLabel = new GuiLabel(TF_DELAY, "cnpcplus.respawn.hidedelay",
                self.guiLeft + 5, self.guiTop + 110, "guihint.cnpcplus.hidedelay");
        this.cnpcplus$delayLabel.enabled = hideOn;
        self.addLabel(this.cnpcplus$delayLabel);
        this.cnpcplus$delayTf = new GuiTextFieldNop(TF_DELAY, self,
                self.guiLeft + 122, self.guiTop + 106, 50, 18,
                String.valueOf(HideDelayStore.get(this.stats)));
        this.cnpcplus$delayTf.numbersOnly = true;
        this.cnpcplus$delayTf.setMinMaxDefault(0, HideDelayStore.MAX_DELAY, HideDelayStore.DEFAULT_DELAY);
        this.cnpcplus$delayTf.enabled = hideOn;
        self.addTextField(this.cnpcplus$delayTf);

        self.addLabel(new GuiLabel(BTN_FX, "cnpcplus.respawn.deathfx",
                self.guiLeft + 5, self.guiTop + 135));
        self.addButton(new GuiButtonNop(self, BTN_FX,
                self.guiLeft + 122, self.guiTop + 130, 80, 20,
                cnpcplus$fxButtonText()));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$openFx(GuiButtonNop button, CallbackInfo ci) {
        if (button == null) return;
        if (button.id == BTN_FX) {
            SubGuiNpcRespawn self = (SubGuiNpcRespawn) (Object) this;
            self.setSubGui(new SubGuiDropFx(this::cnpcplus$applyFx, DeathFxStore.get(this.stats)));
            ci.cancel();
        }
    }

    /** 238c（#2）：隐藏尸体开关（id4，原版只置位不重建）后重算延迟框显隐。 */
    @Inject(method = "buttonEvent", at = @At("TAIL"), remap = false)
    private void cnpcplus$syncDelayBox(GuiButtonNop button, CallbackInfo ci) {
        if (this.cnpcplus$delayTf == null || this.cnpcplus$delayLabel == null) return;
        boolean hideOn = this.stats.hideKilledBody;
        this.cnpcplus$delayTf.enabled = hideOn;
        this.cnpcplus$delayLabel.enabled = hideOn;
    }

    /** 延迟框失焦回写（原版 unFocused 只处理 id 2，不冲突）。 */
    @Inject(method = "unFocused", at = @At("HEAD"), remap = false)
    private void cnpcplus$saveDelay(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield != null && textfield.id == TF_DELAY) {
            HideDelayStore.put(this.stats, textfield.getInteger());
        }
    }

    @Unique
    private void cnpcplus$applyFx(List<DropFxConfig> fxs) {
        DeathFxStore.put(this.stats, fxs);
    }

    @Unique
    private String cnpcplus$fxButtonText() {
        int count = DeathFxStore.size(this.stats);
        if (count <= 0) return net.minecraft.client.resources.language.I18n.get("cnpcplus.respawn.nofx");
        return net.minecraft.client.resources.language.I18n.get("cnpcplus.respawn.fxcount", count);
    }
}
