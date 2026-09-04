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

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$initFloatFields(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties)(Object)this;
        MeleeFloatAccess access = (MeleeFloatAccess) stats;

        self.getTextField(1).numbersOnly = false;
        self.getTextField(1).setFloatsOnly().setMinMaxDefault(0.0f, 99999.0f, 5.0f);
        // Float.toString 不丢失存档中的第二位及更多小数，避免 5.05 回显为 5.1。
        self.getTextField(1).setValue(Float.toString(access.cnpcplus$getStrength()));

        self.getTextField(3).numbersOnly = false;
        self.getTextField(3).setFloatsOnly().setMinMaxDefault(0.0f, 1000.0f, 20.0f);
        self.getTextField(3).setValue(Float.toString(access.cnpcplus$getDelay()));
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$unFocusedFloat(GuiTextFieldNop textfield, CallbackInfo ci) {
        MeleeFloatAccess access = (MeleeFloatAccess) stats;
        if (textfield.id == 1) {
            float val = textfield.getFloat();
            if (val < 0.0f) val = 0.0f;
            access.cnpcplus$setStrength(val);
            ci.cancel();
        } else if (textfield.id == 3) {
            float val = textfield.getFloat();
            if (val < 0.01f) val = 1.0f;
            access.cnpcplus$setDelay(val);
            ci.cancel();
        }
    }
}