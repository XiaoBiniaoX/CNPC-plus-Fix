package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public class MixinSubGuiMeleeProperties {

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;setMinMaxDefault(III)Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;", ordinal = 3), index = 1, remap = false)
    private int modifyKnockbackMax(int max) { return 10; }

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;setMinMaxDefault(III)Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;", ordinal = 3), index = 0, remap = false)
    private int modifyKnockbackMin(int min) { return -10; }

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void onInit(CallbackInfo ci) {
        SubGuiNpcMeleeProperties self = (SubGuiNpcMeleeProperties)(Object)this;
        GuiTextFieldNop tf = self.getTextField(4);
        if (tf != null) {
            tf.numbersOnly = false;
        }
    }
}
