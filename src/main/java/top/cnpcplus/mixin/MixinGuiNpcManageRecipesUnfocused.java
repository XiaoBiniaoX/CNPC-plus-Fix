package top.cnpcplus.mixin;

import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesUnfocused {

    @Inject(method = "unFocused", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$sanitizeRecipeName(GuiTextFieldNop textField, CallbackInfo ci) {
        String raw = textField.getValue();
        if (raw == null || raw.isEmpty()) return;
        String sanitized = raw.replaceAll("[^a-zA-Z0-9/._-]", "_");
        if (!sanitized.equals(raw)) {
            textField.setValue(sanitized);
        }
    }
}
