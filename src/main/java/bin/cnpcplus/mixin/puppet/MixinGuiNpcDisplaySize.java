package bin.cnpcplus.mixin.puppet;

import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public class MixinGuiNpcDisplaySize {

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;setMinMaxDefault(III)Lnoppes/npcs/shared/client/gui/components/GuiTextFieldNop;"), index = 1)
    private int cnpcplus$fixSizeMax(int max) {
        return 100;
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiLabel;<init>(ILjava/lang/String;II)V"), index = 1)
    private String cnpcplus$fixSizeLabel(String label) {
        return "(1-100)";
    }
}
