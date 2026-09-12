package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.MeleeBuffStore;
import bin.cnpcplus.melee.SubGuiMeleeBuffs;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把近战属性界面的「附加效果」换成 BUFF 多选入口（哈基彬需求 B-3）。
 *
 * <h3>需求原文拆解</h3>
 * 「对 NPC 近战攻击列表 原附加效果 进行隐藏，在原位置改为一个按钮，
 * 此按钮默认显示『无 BUFF』，点击后打开一个类似 药水效果职业 的 UI，
 * 支持选择多个 BUFF，设置等级和时长」→ 四件事：
 * <ol>
 *   <li>隐藏原「附加效果」下拉（按钮 id 5）；</li>
 *   <li>原位置放新按钮，默认文字「无 BUFF」；</li>
 *   <li>点击打开多选 UI（{@link SubGuiMeleeBuffs}）；</li>
 *   <li>效果源是全注册表，每项可设等级与时长。</li>
 * </ol>
 *
 * <h3>原版被隐藏的控件</h3>
 * {@code SubGuiNpcMeleeProperties.func_73866_w_}（反编译 41-72 行）：
 * <ul>
 *   <li>标签 5 {@code "stats.meleeeffect"} @ {@code guiLeft+5, guiTop+135}</li>
 *   <li>按钮 5 {@code GuiButtonBiDirectional} @ {@code guiLeft+85, guiTop+130, 100x20}
 *       —— 只有 9 个硬编码选项</li>
 *   <li>标签 6 + 输入框 6（时间）@ {@code guiTop+160}，仅当 effectType != 0 时添加</li>
 *   <li>标签 7 + 按钮 7（强度）@ {@code guiTop+190}，仅当 effectType 既非 0 也非 1 时添加</li>
 * </ul>
 *
 * <h3>隐藏方式：改 enabled 而不是从容器里删</h3>
 * CNPC 的 {@code GuiNPCInterface} 用 {@code Map<Integer, ...>} 存控件，
 * 原版的点击分发（{@code func_146284_a}）与绘制都遍历这些容器。
 * 把控件从 Map 里摘掉需要反射私有字段，而 {@code GuiNpcButton.enabled} /
 * {@code GuiNpcLabel.enabled} / {@code GuiNpcTextField.enabled} 都是 public，
 * 置 false 即不绘制也不响应。这是最小改动，且不破坏原版任何遍历逻辑。
 *
 * <h3>为什么新按钮 id 用 5</h3>
 * 复用原按钮的 id 会让原版 {@code func_146284_a} 的 {@code id == 5} 分支
 * 误触发 {@code setEffect}。所以新按钮另取 id 60（原版用了 1..7 与 66）。
 * 隐藏后的原按钮 5 仍在容器里但 enabled=false，点不到。
 *
 * <h3>时长单位</h3>
 * 原版把「时间」当秒用（{@code EntityNPCInterface.func_70652_k:491} 施加时 ×20），
 * 新 UI 沿用同一单位，避免玩家在两处看到不同含义的数字。
 */
@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public abstract class MixinSubGuiNpcMeleePropertiesBuffs {

    @Shadow(remap = false)
    private DataMelee stats;

    /** 新入口按钮 id。原版占用 1..7 与 66，取 60 避开。 */
    private static final int BTN_BUFFS = 60;

    /**
     * TAIL 注入：等原版把控件都建完再动手，保证 getButton/getLabel 拿得到。
     *
     * 与既有的 {@code MixinSubGuiNpcMeleePropertiesFloat}（也注入同一方法的 TAIL）
     * 互不干扰：那个只碰输入框 1 和 3（攻击力、攻速），这里只碰 5/6/7 号控件。
     */
    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$replaceEffectControl(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;

        // 隐藏原「附加效果」整组控件（下拉、时间、强度及各自标签）。
        // 标签 6/7 与控件 6/7 只在特定 effectType 下才存在，故逐个判空。
        GuiNpcButton oldEffect = self.getButton(5);
        if (oldEffect != null) oldEffect.enabled = false;
        GuiNpcLabel oldEffectLabel = self.getLabel(5);
        if (oldEffectLabel != null) oldEffectLabel.enabled = false;

        GuiNpcTextField oldTime = self.getTextField(6);
        if (oldTime != null) oldTime.enabled = false;
        GuiNpcLabel oldTimeLabel = self.getLabel(6);
        if (oldTimeLabel != null) oldTimeLabel.enabled = false;

        GuiNpcButton oldAmp = self.getButton(7);
        if (oldAmp != null) oldAmp.enabled = false;
        GuiNpcLabel oldAmpLabel = self.getLabel(7);
        if (oldAmpLabel != null) oldAmpLabel.enabled = false;

        // 在原按钮 5 的位置放新入口，宽度也照原来的 100x20。
        self.addLabel(new GuiNpcLabel(60, "stats.meleeeffect",
                self.guiLeft + 5, self.guiTop + 135));
        self.addButton(new GuiNpcButton(BTN_BUFFS,
                self.guiLeft + 85, self.guiTop + 130, 100, 20,
                cnpcplus$buttonText()));
    }

    /**
     * 按钮文字：没配就是「无 BUFF」，配了就显示数量。
     *
     * 用 I18n.format 而不是让 GuiNpcButton 自己翻译：数量要拼进字符串，
     * 而 {@code GuiNpcButton} 的构造器会把整个字符串当 lang 键去查表。
     */
    private String cnpcplus$buttonText() {
        int count = MeleeBuffStore.size(this.stats);
        if (count <= 0) {
            return I18n.format("cnpcplus.melee.nobuff");
        }
        return I18n.format("cnpcplus.melee.buffcount", count);
    }

    /**
     * 点新按钮 → 打开多选界面。
     *
     * cancellable + cancel：原版 {@code func_146284_a} 里 id 60 不匹配任何分支，
     * 本来也不会做别的事，但 cancel 掉可以避免将来上游新增分支时误撞。
     * 关闭子界面时原版 {@code SubGuiInterface.close()} 会回到本界面并重建控件，
     * 按钮文字随之刷新，不需要额外处理。
     */
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private void cnpcplus$openBuffGui(GuiButton guibutton, CallbackInfo ci) {
        if (guibutton == null || guibutton.id != BTN_BUFFS) return;
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        self.setSubGui(new SubGuiMeleeBuffs(this.stats));
        ci.cancel();
    }
}
