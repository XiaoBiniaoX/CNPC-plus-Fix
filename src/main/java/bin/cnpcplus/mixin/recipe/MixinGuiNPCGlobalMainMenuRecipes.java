package bin.cnpcplus.mixin.recipe;

import noppes.npcs.client.gui.mainmenu.GuiNPCGlobalMainMenu;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes intentional "(Broken)" label after init.
 */
@Mixin(GuiNPCGlobalMainMenu.class)
public class MixinGuiNPCGlobalMainMenuRecipes {

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void cnpcplusStripBroken(CallbackInfo ci) {
        GuiNPCGlobalMainMenu self = (GuiNPCGlobalMainMenu) (Object) this;
        try {
            GuiButtonNop btn = self.getButton(14);
            if (btn != null) {
                // rebuild display message without Broken if possible
                btn.setMessage(net.minecraft.network.chat.Component.translatable("global.recipes"));
            }
        } catch (Throwable ignored) {
        }
    }
}