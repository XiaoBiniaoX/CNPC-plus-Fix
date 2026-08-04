package top.cnpcplus.mixin;

import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public class MixinCraftingScreenHideRecipe {

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$hideRecipeBookButton(CallbackInfo ci) {
        CraftingScreen self = (CraftingScreen)(Object)this;
        for (var widget : self.children()) {
            if (widget instanceof StateSwitchingButton btn) {
                btn.visible = false;
                btn.active = false;
            }
        }
    }
}
