package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct screen replacement bypasses native close/save and must still dispose its attached child. */
@Mixin(Screen.class)
public abstract class MixinNpcScreenRemoved {
    @Inject(method = "removed", at = @At("HEAD"), require = 1)
    private void cnpcplus$releaseDetachedPage(CallbackInfo ci) {
        if (!((Object)this instanceof IGuiInterface page)) return;
        var wrapper = page.getWrapper();
        GuiTextFieldNop active = GuiTextFieldNop.getActive();
        if (active != null && wrapper.textfields.containsValue(active)) {
            GuiTextFieldActiveAccess.cnpcplus$clearActive(null);
        }
        // Resize can create a focused field that is not the native static active field.
        for (GuiTextFieldNop field : wrapper.textfields.values()) field.setFocused(false);
        // No unFocused callback: removal must not save an abandoned page.
        Screen child = wrapper.subgui;
        if (child != null) child.removed(); // Keep native parent/draft links for confirmation-screen return.
    }
}
