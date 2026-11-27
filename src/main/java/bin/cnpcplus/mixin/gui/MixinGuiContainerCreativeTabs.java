package bin.cnpcplus.mixin.gui;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiContainerCreative.class)
public abstract class MixinGuiContainerCreativeTabs {
    // Forge 1.12.2 mouseClicked iterates sparse CREATIVE_TAB_ARRAY entries without checking null.
    @Inject(method = "isMouseOverTab", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$skipEmptyTab(CreativeTabs tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> ci) {
        if (tab == null) ci.setReturnValue(false);
    }
}
