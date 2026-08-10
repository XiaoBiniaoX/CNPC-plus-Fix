package bin.cnpcplus.mixin.recipe;

import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Relabel ignoreDamage / ignoreNBT to fuzzy-match meanings via lang overrides.
 * Labels use gui.ignoreDamage / gui.ignoreNBT keys; lang pack handles display.
 */
@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesLabels {

    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplusRelabel(CallbackInfo ci) {
        // Language overrides in assets/customnpcs/lang handle display strings.
    }
}
