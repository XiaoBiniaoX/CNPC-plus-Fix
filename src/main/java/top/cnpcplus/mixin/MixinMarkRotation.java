package top.cnpcplus.mixin;

import noppes.npcs.controllers.data.MarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcplus.marks.MarkRotation;

@Mixin(value = MarkData.Mark.class, remap = false)
public abstract class MixinMarkRotation implements MarkRotation {
    @Unique private boolean cnpcplus$horizontal, cnpcplus$vertical;
    @Override public boolean cnpcplus$horizontal() { return cnpcplus$horizontal; }
    @Override public boolean cnpcplus$vertical() { return cnpcplus$vertical; }
    @Override public void cnpcplus$rotation(boolean horizontal, boolean vertical) { cnpcplus$horizontal = horizontal; cnpcplus$vertical = vertical; }
}
