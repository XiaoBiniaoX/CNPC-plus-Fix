package top.cnpcplus.mixin;

import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiTextFieldNop.class, remap = false)
public interface GuiTextFieldActiveAccess {
    @Accessor("activeTextfield") static void cnpcplus$clearActive(GuiTextFieldNop field) { throw new AssertionError(); }
}
