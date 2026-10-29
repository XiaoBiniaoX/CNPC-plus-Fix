package top.cnpcplus.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractWidget.class, remap = false)
public interface AbstractWidgetAccess {

    @Accessor("width")
    void cnpcplus$setWidth(int width);

    @Accessor("x")
    void cnpcplus$setX(int x);

    @Accessor("y")
    void cnpcplus$setY(int y);
}
