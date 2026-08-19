package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A5: 近战击退输入框
 * 1) 保留负数支持（用户明确要求），min=-10、max=10 与原版扩展一致。
 * 2) 修复「留空/非法字符串保存崩溃」：unFocused 时对空串或非数字补回当前值，避免 NumberFormatException。
 */
@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public class MixinSubGuiMeleeProperties {

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;setMinMaxDefault(III)Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;", ordinal = 3), index = 1, remap = false)
    private int modifyKnockbackMax(int max) { return 10; }

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;setMinMaxDefault(III)Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;", ordinal = 3), index = 0, remap = false)
    private int modifyKnockbackMin(int min) { return -10; }

    @Inject(method = "unFocused", at = @At("HEAD"), remap = false, cancellable = true)
    private void onKnockbackUnfocused(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield.id != 4) return;
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        int val;
        if (textfield.getValue() == null || textfield.getValue().trim().isEmpty()) {
            textfield.setValue("0");
            val = 0;
        } else {
            try {
                val = Integer.parseInt(textfield.getValue().trim());
            } catch (NumberFormatException e) {
                textfield.setValue("0");
                val = 0;
            }
        }
        ((SubGuiNpcMeleePropertiesAccess) self).cnpcplus$getStats().setKnockback(val);
        ci.cancel();
    }
}