package top.cnpcplus.mixin;

import noppes.npcs.shared.client.gui.GuiTextAreaScreen;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiTextAreaScreen.class, remap = false)
public interface GuiTextAreaScreenAccess {
    @Accessor("textarea") void cnpcplus$area(GuiTextArea area);
}
