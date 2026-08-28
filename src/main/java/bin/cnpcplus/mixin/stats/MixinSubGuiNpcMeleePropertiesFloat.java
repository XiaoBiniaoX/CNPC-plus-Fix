package bin.cnpcplus.mixin.stats;

import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.data.DataMelee;
import bin.cnpcplus.common.IDataMeleeFloatAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public abstract class MixinSubGuiNpcMeleePropertiesFloat {

    @Shadow(remap = false)
    private DataMelee stats;

    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void cnpcplus$onInitGui(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        IDataMeleeFloatAccess acc = (IDataMeleeFloatAccess) this.stats;
        GuiNpcTextField strengthField = self.getTextField(1);
        if (strengthField != null) {
            strengthField.numbersOnly = false;
            strengthField.setText(String.valueOf(acc.cnpcplus$getStrengthFloat()));
        }
        GuiNpcTextField speedField = self.getTextField(3);
        if (speedField != null) {
            speedField.numbersOnly = false;
            speedField.setText(String.valueOf(acc.cnpcplus$getDelayFloat()));
        }
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onUnfocused(GuiNpcTextField textfield, CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties) (Object) this;
        IDataMeleeFloatAccess acc = (IDataMeleeFloatAccess) this.stats;
        if (self.getTextField(1) == textfield) {
            String text = textfield.getText().trim();
            float val = 5.0f;
            if (!text.isEmpty()) {
                try {
                    val = Float.parseFloat(text);
                } catch (NumberFormatException e) {
                    val = 5.0f;
                }
            }
            if (val < 0.0f) {
                val = 0.0f;
            }
            acc.cnpcplus$setStrengthFloat(val);
            ci.cancel();
            return;
        }
        if (self.getTextField(3) == textfield) {
            String text = textfield.getText().trim();
            float val = 20.0f;
            if (!text.isEmpty()) {
                try {
                    val = Float.parseFloat(text);
                } catch (NumberFormatException e) {
                    val = 20.0f;
                }
            }
            if (val <= 0.0f) {
                val = 1.0f;
            }
            acc.cnpcplus$setDelayFloat(val);
            ci.cancel();
        }
    }
}