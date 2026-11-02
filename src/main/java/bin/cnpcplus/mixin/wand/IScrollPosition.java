package bin.cnpcplus.mixin.wand;

import noppes.npcs.client.gui.util.GuiCustomScroll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiCustomScroll.class, remap = false)
public interface IScrollPosition {
    @Accessor("scrollY") int cnpcplus$scrollY();
}
