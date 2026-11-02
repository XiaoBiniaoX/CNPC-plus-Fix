package bin.cnpcplus.mixin.advanced;

import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiNpcButton.class, remap = false)
public interface IGuiButtonOptions {
    @Accessor("display") String[] cnpcplus$options();
}
