package bin.cnpcplus.mixin.recipe;

import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes intentional "(Broken)" label from recipes button (id 14).
 */
@Mixin(GuiNPCGlobalMainMenu.class)
public class MixinGuiNPCGlobalMainMenuRecipes {

    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplusStripBroken(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        try {
            GuiNpcButton btn = self.getButton(14);
            if (btn != null) {
                btn.setDisplayText("global.recipes");
            }
        } catch (Throwable ignored) {
        }
    }
}
