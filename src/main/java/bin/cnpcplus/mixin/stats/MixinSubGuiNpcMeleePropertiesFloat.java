package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.accessor.MeleeFloatAccess;
import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.entity.data.DataMelee;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public class MixinSubGuiNpcMeleePropertiesFloat {

    @Shadow
    private DataMelee stats;

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void cnpcplus$initFloatFields(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties)(Object)this;
        MeleeFloatAccess access = (MeleeFloatAccess) stats;

        self.getTextField(1).numbersOnly = false;
        self.getTextField(1).setFloatsOnly().setMinMaxDefault(0.0f, Float.MAX_VALUE, 5.0f);
        // Float.toString 不丢失存档中的第二位及更多小数，避免 5.05 回显为 5.1。
        self.getTextField(1).setValue(Float.toString(access.cnpcplus$getStrength()));

        self.getTextField(3).numbersOnly = false;
        self.getTextField(3).setFloatsOnly().setMinMaxDefault(0.1f, Float.MAX_VALUE, 20.0f);
        self.getTextField(3).setValue(Float.toString(access.cnpcplus$getDelay()));
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$unFocusedFloat(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield.id != 1 && textfield.id != 3) return;
        MeleeFloatAccess access = (MeleeFloatAccess) stats;
        // GuiTextFieldNop has already parsed/clamped the field before invoking its listener.
        if (textfield.id == 1) {
            access.cnpcplus$setStrength(textfield.getFloat());
            textfield.setValue(Float.toString(access.cnpcplus$getStrength()));
        } else {
            access.cnpcplus$setDelay(textfield.getFloat());
            textfield.setValue(Float.toString(access.cnpcplus$getDelay()));
        }
        ci.cancel();
    }
}
