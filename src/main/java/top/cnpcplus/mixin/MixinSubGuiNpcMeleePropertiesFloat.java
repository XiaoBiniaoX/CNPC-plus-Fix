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
            ExtraDataStorage.setFloat(stats, 3, val);
            stats.setStrength(Math.round(val));
            ci.cancel();
        } else if (textfield.id == 3) {
            float val;
            if (textfield.getValue() == null || textfield.getValue().trim().isEmpty()) {
                val = 1.0f;
            } else {
                try { val = Float.parseFloat(textfield.getValue().trim()); } catch (NumberFormatException e) { val = 1.0f; }
            }
            if (val <= 0.0f) val = 1.0f;
            ExtraDataStorage.setFloat(stats, 4, val);
            stats.setDelay(Math.round(val));
            ci.cancel();
        }
    }
}