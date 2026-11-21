package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.entity.data.DataMelee;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.melee.MeleeFloatValues;

@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public class MixinSubGuiNpcMeleePropertiesFloat {

    @Shadow(remap = false)
    private DataMelee stats;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$convertFieldsToFloat(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        MeleeFloatValues values = (MeleeFloatValues) stats;
        GuiTextFieldNop strengthField = self.getTextField(1);
        if (strengthField != null) {
            strengthField.numbersOnly = false;
            strengthField.floatsOnly = true;
            strengthField.setMinMaxDefault(0.0f, Float.MAX_VALUE, 5.0f);
            strengthField.setValue(String.valueOf(values.cnpcplus$getStrengthFloat()));
        }
        self.getTextField(2).minF = 0.1f;
        GuiTextFieldNop speedField = self.getTextField(3);
        if (speedField != null) {
            speedField.numbersOnly = false;
            speedField.floatsOnly = true;
            speedField.setMinMaxDefault(0.1f, Float.MAX_VALUE, 20.0f);
            speedField.setValue(String.valueOf(values.cnpcplus$getDelayFloat()));
        }
    }

    @Inject(method = "unFocused", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleFloatUnfocused(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield.id != 1 && textfield.id != 3) return;
        // GuiTextFieldNop has already parsed/clamped the field before invoking its listener.
        MeleeFloatValues values = (MeleeFloatValues) stats;
        if (textfield.id == 1) {
            values.cnpcplus$setStrengthFloat(textfield.getFloat());
            textfield.setValue(Float.toString(values.cnpcplus$getStrengthFloat()));
        } else {
            values.cnpcplus$setDelayFloat(textfield.getFloat());
            textfield.setValue(Float.toString(values.cnpcplus$getDelayFloat()));
        }
        ci.cancel();
    }
}
