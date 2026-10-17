package top.cnpcplus.mixin;

import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.SubGuiNpcProjectiles;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.client.SubGuiDropFx;
import top.cnpcplus.melee.SubGuiMeleeBuffs;
import top.cnpcplus.ranged.RangedBuffStore;
import top.cnpcplus.ranged.RangedFxStore;
import top.cnpcplus.ranged.RangedSpinStore;

import java.util.List;

/**
 * 弹射物设置的「效果 / 尾迹类型」入口重做（234 派发「原CNPC入口重做」）。
 *
 * <h3>原版被替换的控件（反编译 {@code SubGuiNpcProjectiles.m_7856_}）</h3>
 * <ul>
 *   <li><b>效果</b>：按钮 4 {@code GuiButtonBiDirectional} @ (40,160,100×20)
 *       —— 硬编码 34 项数字 id 表吃不到 mod 效果；条件添加的时长框 tf5 @ (140,160)
 *       与强化钮 10 @ (210,160) 一并隐藏。<b>标签 7「效果」保留</b>（标题复用）。</li>
 *   <li><b>尾迹</b>：按钮 5 循环钮 @ (60,190,60×20) —— 换粒子特效编辑器入口。
 *       <b>标签 8「尾迹」保留</b>。发光钮 6 @ (140,190) 与完成钮 66 不动，
 *       所以新入口钮 61 宽度取 76（60..136 &lt; 140 不重叠）。</li>
 * </ul>
 *
 * <h3>新入口</h3>
 * 按钮 60 = 多 BUFF 面板（复用近战 {@link SubGuiMeleeBuffs}，
 * 数据换 {@link RangedBuffStore}，「注意对象」落地）；
 * 按钮 61 = 粒子特效编辑器（复用掉落物 {@link SubGuiDropFx}，Consumer 回写到
 * {@link RangedFxStore}）。关闭子界面时 {@code GuiWrapper.close()} 回本界面
 * 重建控件，按钮数量文字自动刷新（近战同款机制）。
 *
 * <h3>隐藏方式（近战 MixinSubGuiMeleePropertiesBuffs 已验证的三条）</h3>
 * 按钮 {@code shown/visible/active=false}（1.20.1 无 public enabled）；
 * 标签/输入框 {@code enabled=false}；不从容器摘控件（GuiWrapper 双容器遍历不破）。
 *
 * <h3>id 规避</h3>
 * 原版占用 0..10 与 66，取 60/61（与近战入口 60 不同类不冲突）。
 * {@code buttonEvent} HEAD cancellable：cancel 掉防将来上游新增分支误撞。
 */
@Mixin(value = SubGuiNpcProjectiles.class, remap = false)
public abstract class MixinSubGuiNpcProjectilesExtras {

    @Shadow(remap = false)
    private DataRanged stats;

    private static final int BTN_BUFFS = 60;
    private static final int BTN_FX = 61;
    private static final int TF_SPIN = 12;

    @Unique
    private GuiTextFieldNop cnpcplus$spinTf;

    @Unique
    private GuiLabel cnpcplus$spinLabel;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$replaceEffectAndTrailControls(CallbackInfo ci) {
        SubGuiNpcProjectiles self = (SubGuiNpcProjectiles) (Object) this;

        // --- 效果行（y≈160）：藏循环钮/时长框/强化钮，标签 7 保留 ---
        GuiButtonNop oldEffect = self.getButton(4);
        if (oldEffect != null) {
            oldEffect.shown = false;
            oldEffect.visible = false;
            oldEffect.active = false;
        }
        GuiTextFieldNop oldTime = self.getTextField(5);
        if (oldTime != null) oldTime.enabled = false;
        GuiButtonNop oldAmp = self.getButton(10);
        if (oldAmp != null) {
            oldAmp.shown = false;
            oldAmp.visible = false;
            oldAmp.active = false;
        }
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_BUFFS,
                self.guiLeft + 40, self.guiTop + 160, 100, 20,
                cnpcplus$buffButtonText()));

        // --- 尾迹行（y≈190）：藏循环钮 5，标签 8 保留 ---
        GuiButtonNop oldTrail = self.getButton(5);
        if (oldTrail != null) {
            oldTrail.shown = false;
            oldTrail.visible = false;
            oldTrail.active = false;
        }
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_FX,
                self.guiLeft + 60, self.guiTop + 190, 76, 20,
                cnpcplus$fxButtonText()));

        // --- 238-9/238c：自旋速度系数框（0=原版 10/20 公式，负数=反旋）。
        //     门槛=getRender3D && getSpins（反馈：仅自旋开时显示）；恒建+enabled 显隐
        //     （库内既有手法），buttonEvent TAIL 随开关翻转。y130 行右侧无原版控件；
        //     宽 45 → 190+45=235 ≤ 256。minmax 放开负区间（原 minF 默认 0，
        //     失焦校验会把负数洗成 0）---
        boolean showSpin = this.stats.getRender3D() && this.stats.getSpins();
        this.cnpcplus$spinLabel = new GuiLabel(
                TF_SPIN, "cnpcplus.projectile.spinspeed",
                self.guiLeft + 140, self.guiTop + 135, "guihint.cnpcplus.spinspeed");
        this.cnpcplus$spinLabel.enabled = showSpin;
        self.addLabel(this.cnpcplus$spinLabel);
        this.cnpcplus$spinTf = new GuiTextFieldNop(TF_SPIN, self,
                self.guiLeft + 190, self.guiTop + 130, 45, 18,
                cnpcplus$spinText());
        this.cnpcplus$spinTf.numbersOnly = false;
        this.cnpcplus$spinTf.floatsOnly = true;
        this.cnpcplus$spinTf.setMinMaxDefault(-RangedSpinStore.MAX_SPIN, RangedSpinStore.MAX_SPIN, 0);
        this.cnpcplus$spinTf.enabled = showSpin;
        self.addTextField(this.cnpcplus$spinTf);
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$openSubGuis(GuiButtonNop button, CallbackInfo ci) {
        if (button == null) return;
        SubGuiNpcProjectiles self = (SubGuiNpcProjectiles) (Object) this;
        if (button.id == BTN_BUFFS) {
            self.setSubGui(new SubGuiMeleeBuffs(RangedBuffStore.tableFor(this.stats)));
            ci.cancel();
        } else if (button.id == BTN_FX) {
            self.setSubGui(new SubGuiDropFx(
                    this::cnpcplus$applyFx, RangedFxStore.get(this.stats)));
            ci.cancel();
        }
    }

    /** 238c：render3D（id7）/自旋（id8）开关后重算自旋框显隐（原版这两个按钮不重建）。 */
    @Inject(method = "buttonEvent", at = @At("TAIL"), remap = false)
    private void cnpcplus$syncSpinBox(GuiButtonNop button, CallbackInfo ci) {
        if (this.cnpcplus$spinTf == null || this.cnpcplus$spinLabel == null) return;
        boolean show = this.stats.getRender3D() && this.stats.getSpins();
        this.cnpcplus$spinTf.enabled = show;
        this.cnpcplus$spinLabel.enabled = show;
    }

    /** 238-9：自旋系数框失焦回写（原版 unFocused 只处理 id 1..5，不冲突）。 */
    @Inject(method = "unFocused", at = @At("HEAD"), remap = false)
    private void cnpcplus$saveSpin(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield != null && textfield.id == TF_SPIN) {
            try {
                RangedSpinStore.put(this.stats, Float.parseFloat(textfield.getValue()));
            } catch (NumberFormatException ignored) {
                RangedSpinStore.put(this.stats, 0.0f);
            }
        }
    }

    @Unique
    private String cnpcplus$spinText() {
        float v = RangedSpinStore.get(this.stats);
        return v == 0.0f ? "0" : String.valueOf(v);
    }

    /** 确定回写：整表替换进 RangedFxStore（落盘随 STATS 菜单包，见 Store 类注释）。 */
    @Unique
    private void cnpcplus$applyFx(List<DropFxConfig> fxs) {
        RangedFxStore.put(this.stats, fxs);
    }

    /**
     * 按钮 60 文案：复用近战键（「无BUFF / 已配 %s 个」）。
     * 必须先 {@code I18n.get} 再交给 GuiButtonNop——数量拼进字符串后不再是合法 lang 键。
     */
    @Unique
    private String cnpcplus$buffButtonText() {
        int count = RangedBuffStore.size(this.stats);
        if (count <= 0) return I18n.get("cnpcplus.melee.nobuff");
        return I18n.get("cnpcplus.melee.buffcount", count);
    }

    @Unique
    private String cnpcplus$fxButtonText() {
        int count = RangedFxStore.size(this.stats);
        if (count <= 0) return I18n.get("cnpcplus.projectile.nofx");
        return I18n.get("cnpcplus.projectile.fxcount", count);
    }
}
