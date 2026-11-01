package top.cnpcplus.mixin;

import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiCustomScrollNop.class, remap = false)
public interface WandScrollAccess {
    @Accessor("scrollY") int cnpcplus$scrollY();
    @Accessor("scrollY") void cnpcplus$scrollY(int value);
    @Accessor("maxScrollY") int cnpcplus$maxScrollY();
}
