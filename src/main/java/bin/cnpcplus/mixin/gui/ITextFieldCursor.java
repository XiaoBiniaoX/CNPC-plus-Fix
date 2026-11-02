package bin.cnpcplus.mixin.gui;

import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiTextField.class, remap = true)
public interface ITextFieldCursor {
    @Accessor("cursorPosition") void cnpcplus$anchor(int index);
}
