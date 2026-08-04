package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcRangeProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = SubGuiNpcRangeProperties.class, remap = false)
public class MixinSubGuiRangeProperties {

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;setMinMaxDefault(III)Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;", ordinal = 2), index = 1, remap = false)
    private int modifyRangeMax(int max) { return 256; }
}
