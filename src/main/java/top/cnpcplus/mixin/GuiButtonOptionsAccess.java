package top.cnpcplus.mixin;

import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiButtonNop.class, remap = false)
public interface GuiButtonOptionsAccess {
    @Accessor("display") String[] cnpcplus$options();
}
