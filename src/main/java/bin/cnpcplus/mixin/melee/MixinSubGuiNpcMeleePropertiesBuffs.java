package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.MeleeBuff;
import bin.cnpcplus.melee.MeleeBuffsAccess;
import bin.cnpcplus.melee.client.SubGuiMeleeBuffs;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.entity.data.DataMelee;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 把近战属性界面的「附加效果」换成 BUFF 多选入口。
 *
 * <p>按 1.12.2 已完成的实现对齐：原版整组控件（效果下拉 id 5、时长 id 6、等级 id 7
 * 以及各自标签）全部隐藏，在原按钮 5 的位置放新入口按钮，保留「近战附加效果」标签，
 * 按钮默认显示「无BUFF」、配了则显示数量。
 *
 * <p>隐藏方式是置 {@code shown}/{@code enabled} 为 false 而不是从容器里摘掉：
 * CNPC 用 Map 存控件且绘制与点击分发都遍历这些容器，摘除需要反射私有字段，
 * 而这两个标志位都是 public，置 false 即不绘制也不响应，是最小改动。
 *
 * <p>新按钮 id 取 60（原版占用 1..7 与 66），不能复用 id 5 ——
 * 否则原版 {@code buttonEvent} 的 {@code id == 5} 分支会误触发 {@code setEffect}。
 *
 * <p>与已有的 {@code MixinSubGuiNpcMeleePropertiesFloat}（也注入同一方法 TAIL）
 * 互不干扰：那个只碰输入框 1 和 3（攻击力、攻速），这里只碰 5/6/7 号控件。
 */
@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public class MixinSubGuiNpcMeleePropertiesBuffs {

    @Shadow
    private DataMelee stats;

    /** 新入口按钮 id。原版占用 1..7 与 66，取 60 避开。 */
    private static final int CNPCPLUS_BTN_BUFFS = 60;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$replaceEffectControls(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        if (this.stats == null) return;

        // 隐藏原「附加效果」整组控件。
        // 标签 6/7 与控件 6/7 只在特定 effectType 下才存在，故逐个判空。
        cnpcplus$hideButton(self.getButton(5));
        cnpcplus$hideButton(self.getButton(7));

        GuiLabel oldEffectLabel = self.getLabel(5);
        if (oldEffectLabel != null) oldEffectLabel.enabled = false;

        GuiTextFieldNop oldTime = self.getTextField(6);
        if (oldTime != null) oldTime.enabled = false;
        GuiLabel oldTimeLabel = self.getLabel(6);
        if (oldTimeLabel != null) oldTimeLabel.enabled = false;

        GuiLabel oldAmpLabel = self.getLabel(7);
        if (oldAmpLabel != null) oldAmpLabel.enabled = false;

        // 在原位置放新入口，保留原标签文案与坐标、按钮沿用 100x20。
        self.addLabel(new GuiLabel(60, I18n.get("stats.meleeeffect"),
                self.guiLeft + 5, self.guiTop + 135));
        self.addButton(new GuiButtonNop((IGuiInterface) self, CNPCPLUS_BTN_BUFFS,
                self.guiLeft + 85, self.guiTop + 130, 100, 20, cnpcplus$buttonText()));
    }

    /**
     * 真正藏掉一个原版按钮。
     *
     * <p>只置 {@code shown = false} 是不够的 —— 那是 CNPC 自己加的字段，
     * 只在 {@code GuiButtonNop.renderWidget:86-91} 里用来跳过绘制。
     * 而点击路由走的是原版 {@code AbstractWidget}：{@code mouseClicked:157-158} 与
     * {@code clicked:218-223} 判的都是 {@code active && visible}，跟 {@code shown} 无关。
     * 于是原按钮虽然看不见，却仍然占着同一块矩形抢走点击 —— 而且它比新按钮先注册，
     * 表现就是「新按钮点不进去」。
     *
     * <p>所以三个标志位都要压：{@code visible} 断掉点击与命中测试，
     * {@code active} 双保险（原版两处判定都要求它为真），
     * {@code shown} 让 CNPC 自己的绘制分支也跳过。
     */
    private static void cnpcplus$hideButton(GuiButtonNop button) {
        if (button == null) return;
        button.shown = false;
        button.visible = false;
        button.active = false;
    }

    /**
     * 按钮文字：没配就是「无BUFF」，配了就显示数量。
     *
     * <p>用 {@code I18n.get} 自己翻译而不是把键交给 GuiButtonNop：
     * 数量要拼进字符串，而按钮构造器会把整个字符串当语言键去查表。
     */
    private String cnpcplus$buttonText() {
        List<MeleeBuff> buffs = ((MeleeBuffsAccess) this.stats).cnpcplus$getMeleeBuffs();
        int count = buffs == null ? 0 : buffs.size();
        if (count <= 0) {
            return I18n.get("cnpcplus.melee.noBuff");
        }
        return I18n.get("cnpcplus.melee.buffCount", count);
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$openBuffGui(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || button.id != CNPCPLUS_BTN_BUFFS) return;
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        if (this.stats == null) return;

        // 新界面接管附加效果后，把原版单效果字段清零：
        // 否则 doHurtTarget 里原版那段（:535-540）会和 BUFF 表各施加一次，出现重复效果。
        if (this.stats.getEffectType() != 0) {
            this.stats.setEffect(0, this.stats.getEffectStrength(), this.stats.getEffectTime());
        }
        self.setSubGui(new SubGuiMeleeBuffs(this.stats));
        ci.cancel();
    }
}
