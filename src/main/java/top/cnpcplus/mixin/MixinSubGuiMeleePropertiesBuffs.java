package top.cnpcplus.mixin;

import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.entity.data.DataMelee;
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
import top.cnpcplus.melee.MeleeBuffStore;
import top.cnpcplus.melee.SubGuiMeleeBuffs;

/**
 * 把近战属性界面的「附加效果」换成 BUFF 多选入口（哈基彬需求 A3）。
 *
 * <h3>需求拆解</h3>
 * 「对 NPC 近战攻击列表 原附加效果 进行隐藏，在原位置改为一个按钮，
 * 此按钮默认显示『无BUFF』，点击后打开一个类似 药水效果职业 的 UI，
 * 支持选择多个 BUFF，设置等级和时长」→ 四件事：
 * <ol>
 *   <li>隐藏原「附加效果」控件组；</li>
 *   <li>原位置放新按钮，默认文字「无BUFF」；</li>
 *   <li>点击打开多选 UI（{@link SubGuiMeleeBuffs}）；</li>
 *   <li>效果源是全注册表，每项可设等级与时长。</li>
 * </ol>
 *
 * <h3>原版被隐藏的控件（反编译 {@code SubGuiNpcMeleeProperties.m_7856_}）</h3>
 * <ul>
 *   <li>{@code :61} 标签 5 {@code "stats.meleeeffect"} @ {@code guiLeft+5, guiTop+135}</li>
 *   <li>{@code :66} 按钮 5 {@code GuiButtonBiDirectional} @ {@code guiLeft+85, guiTop+130, 100x20}
 *       —— 只有静态块里硬编码的 34 个选项，取不到 mod 效果</li>
 *   <li>{@code :68-71} 标签 6 + 输入框 6（时间）@ {@code guiTop+165/160}，仅 effectType != 0 时添加</li>
 *   <li>{@code :73-74} 标签 7 + 按钮 7（强度）@ {@code guiTop+195/190}，仅 effectType 既非 0 也非 666 时添加</li>
 * </ul>
 * 后两组是条件添加的，所以逐个判空。
 *
 * <h3>隐藏方式（1.20.1 与 1.12.2 不同，易错）</h3>
 * <ul>
 *   <li>按钮用 <b>{@code shown = false}</b>：{@code GuiButtonNop.m_88315_}
 *       （反编译 86-91 行）开头就是 {@code if (!this.shown) return;}。
 *       1.20.1 的 {@code GuiButtonNop} **没有** public 的 {@code enabled} 字段
 *       （1.12.2 有），只有 {@code setEnabled()} 改 {@code f_93623_}，
 *       而那只是让按钮变灰仍可见 —— 不是我们要的。</li>
 *   <li>标签与输入框用 <b>{@code enabled = false}</b>：{@code GuiLabel.m_88315_:81}
 *       与 {@code GuiTextFieldNop.m_87963_:181} 都是 {@code if (!this.enabled) return;}，
 *       这两个字段在 1.20.1 仍是 public。</li>
 * </ul>
 * 不从容器里摘控件：{@code GuiWrapper} 用 {@code ConcurrentHashMap} 存控件，
 * 原版的点击分发与绘制都遍历这些容器，摘掉需要动 private 结构，
 * 而置标志位是最小改动且不破坏任何原版遍历。
 *
 * <h3>为什么新按钮另取 id 60</h3>
 * 复用原按钮 id 5 会让原版 {@code buttonEvent} 的 {@code id == 5} 分支
 * 误触发 {@code setEffect}。原版占用 1..7 与 66，取 60 避开。
 *
 * <h3>与其他同目标 mixin 的关系</h3>
 * {@code MixinSubGuiNpcMeleePropertiesFloat} 也注入同一个 {@code m_7856_} 的 TAIL，
 * 但它只碰输入框 1 和 3（攻击力、攻速）；{@code MixinSubGuiMeleeProperties} 注入
 * {@code unFocused} 的 HEAD 只处理 id 4（击退）。三者互不重叠。
 *
 * <h3>时长单位</h3>
 * 原版把「时间」当秒用（{@code EntityNPCInterface:564} 施加时 ×20），
 * 新 UI 沿用同一单位，避免玩家在两处看到不同含义的数字。
 */
@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public abstract class MixinSubGuiMeleePropertiesBuffs {

    @Shadow(remap = false)
    private DataMelee stats;

    /** 新入口按钮/标签 id。原版占用 1..7 与 66，取 60 避开。 */
    private static final int BTN_BUFFS = 60;

    /**
     * TAIL 注入：等原版把控件都建完再动手，保证 getButton/getLabel 拿得到。
     */
    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$replaceEffectControl(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;

        // 隐藏原「附加效果」整组控件（下拉、时间、强度及各自标签）。
        GuiButtonNop oldEffect = self.getButton(5);
        if (oldEffect != null) oldEffect.shown = false;
        GuiLabel oldEffectLabel = self.getLabel(5);
        if (oldEffectLabel != null) oldEffectLabel.enabled = false;

        GuiTextFieldNop oldTime = self.getTextField(6);
        if (oldTime != null) oldTime.enabled = false;
        GuiLabel oldTimeLabel = self.getLabel(6);
        if (oldTimeLabel != null) oldTimeLabel.enabled = false;

        GuiButtonNop oldAmp = self.getButton(7);
        if (oldAmp != null) oldAmp.shown = false;
        GuiLabel oldAmpLabel = self.getLabel(7);
        if (oldAmpLabel != null) oldAmpLabel.enabled = false;

        // 在原按钮 5 的位置放新入口，宽高照原来的 100x20。
        self.addLabel(new GuiLabel(BTN_BUFFS, "stats.meleeeffect",
                self.guiLeft + 5, self.guiTop + 135));
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_BUFFS,
                self.guiLeft + 85, self.guiTop + 130, 100, 20,
                cnpcplus$buttonText()));
    }

    /**
     * 按钮文字：没配就是「无BUFF」，配了就显示数量。
     *
     * <p>这里必须先 {@code I18n.get} 再交给 {@code GuiButtonNop}：那个构造器内部走
     * {@code Component.translatable}，会把整个字符串当 lang 键。数量要拼进字符串，
     * 拼完就不再是合法键了，所以自己先翻译好 —— 翻译结果查不到表就原样显示，正是想要的。
     */
    @Unique
    private String cnpcplus$buttonText() {
        int count = MeleeBuffStore.size(this.stats);
        if (count <= 0) {
            return I18n.get("cnpcplus.melee.nobuff");
        }
        return I18n.get("cnpcplus.melee.buffcount", count);
    }

    /**
     * 点新按钮 → 打开多选界面。
     *
     * <p>cancellable + cancel：原版 {@code buttonEvent} 里 id 60 不匹配任何分支，
     * 本来也不会做别的事，但 cancel 掉可以避免将来上游新增分支时误撞。
     * 关闭子界面时 {@code GuiWrapper.close()} 会回到本界面并重建控件，
     * 按钮文字随之刷新，不需要额外处理。
     */
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$openBuffGui(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || button.id != BTN_BUFFS) return;
        ((SubGuiNpcMeleeProperties) (Object) this).setSubGui(new SubGuiMeleeBuffs(this.stats));
        ci.cancel();
    }
}
