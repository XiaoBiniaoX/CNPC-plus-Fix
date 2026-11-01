package top.cnpcplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "noppes.npcs.shared.client.gui.components.TextContainer$LineData", remap = false)
public interface TextLineAccess {
    @Accessor("text") String cnpcplus$text();
    @Accessor("start") int cnpcplus$start();
    @Accessor("end") int cnpcplus$end();
}
