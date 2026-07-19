package bin.cnpcplus.mixin.recipe;

import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Relabel official ignoreDamage / ignoreNBT toggles to 1.20.1 cnpcplus meanings.
 * Uses language keys gui.ignoreDamage / gui.ignoreNBT overridden in cnpcplus lang.
 */
@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesLabels {

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void cnpcplusRelabel(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        // buttons already use gui.ignoreDamage / gui.ignoreNBT keys;
        // language override handles display. Force refresh messages.
        try {
            GuiButtonNop d = self.getButton(5);
            if (d != null) {
                d.setMessage(net.minecraft.network.chat.Component.translatable("gui.ignoreDamage"));
            }
            GuiButtonNop n = self.getButton(6);
            if (n != null) {
                n.setMessage(net.minecraft.network.chat.Component.translatable("gui.ignoreNBT"));
            }
        } catch (Throwable ignored) {
        }
    }
}