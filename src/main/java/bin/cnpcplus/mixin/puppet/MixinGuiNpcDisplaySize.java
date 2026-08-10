package bin.cnpcplus.mixin.puppet;

import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public class MixinGuiNpcDisplaySize {

    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplus$extendSizeUi(CallbackInfo ci) {
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        try {
            GuiNpcTextField field = self.getTextField(2);
            if (field != null) {
                field.setMinMaxDefault(1, 100, 5);
            }
            GuiNpcLabel label = self.getLabel(3);
            if (label != null) {
                // label text is private; recreate via set if available, else ignore
            }
        } catch (Throwable ignored) {
        }
    }
}
