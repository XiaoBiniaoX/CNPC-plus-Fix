package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.entity.data.DataMelee;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public class MixinSubGuiNpcMeleePropertiesFloat {

    @Shadow(remap = false)
    private DataMelee stats;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$convertFieldsToFloat(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        GuiTextFieldNop strengthField = self.getTextField(1);
        if (strengthField != null) {
            strengthField.numbersOnly = false;
            strengthField.floatsOnly = true;
            strengthField.setMinMaxDefault(0.0f, Float.MAX_VALUE, 5.0f);
            float v = ExtraDataStorage.getFloat(stats, 3);
            strengthField.setValue(String.valueOf(v < 0.0f ? (float) stats.getStrength() : v));
        }
        GuiTextFieldNop speedField = self.getTextField(3);
        if (speedField != null) {
            speedField.numbersOnly = false;
            speedField.floatsOnly = true;
            speedField.setMinMaxDefault(0.0f, Float.MAX_VALUE, 20.0f);
            float v = ExtraDataStorage.getFloat(stats, 4);
            speedField.setValue(String.valueOf(v < 0.0f ? (float) stats.getDelay() : v));
        }
    }

    @Inject(method = "unFocused", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleFloatUnfocused(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield.id == 1) {
            float val;
            if (textfield.getValue() == null || textfield.getValue().trim().isEmpty()) {
                val = 0.0f;
            } else {
                try { val = Float.parseFloat(textfield.getValue().trim()); } catch (NumberFormatException e) { val = 0.0f; }
            }
            // 顺序至关重要：必须先调整数 setter，再写小数值。
            // setStrength 的 RETURN 注入（MixinDataMeleeFloat.cnpcplus$syncStrengthFromInt）会
            // 无条件把小数槽覆盖成 Math.round 的整数——那是「脚本用整数 API 即放弃精度」的语义。
            // 旧代码顺序相反（先 setFloat 再 setStrength），于是玩家输入的 7.5 被自己的同步注入
            // 立刻改写成 8.0，表现为「离开编辑界面小数被四舍五入/回弹」。
            stats.setStrength(Math.round(val));
            ExtraDataStorage.setFloat(stats, 3, val);
            ci.cancel();
        } else if (textfield.id == 3) {
            float val;
            if (textfield.getValue() == null || textfield.getValue().trim().isEmpty()) {
                val = 1.0f;
            } else {
                try { val = Float.parseFloat(textfield.getValue().trim()); } catch (NumberFormatException e) { val = 1.0f; }
            }
            if (val <= 0.0f) val = 1.0f;
            // 同上：setDelay 的 RETURN 注入会覆盖小数槽，必须放在 setFloat 之前。
            stats.setDelay(Math.round(val));
            ExtraDataStorage.setFloat(stats, 4, val);
            ci.cancel();
        }
    }
}