package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccess {
    @Accessor("leftPos")
    int cnpcplus$getLeftPos();
    @Accessor("leftPos")
    void cnpcplus$setLeftPos(int left);
    @Accessor("topPos")
    int cnpcplus$getTopPos();
    @Accessor("topPos")
    void cnpcplus$setTopPos(int top);
    @Accessor("imageHeight")
    void cnpcplus$setImageHeight(int height);
    @Accessor("imageHeight")
    int cnpcplus$getImageHeight();
    @Accessor("menu")
    AbstractContainerMenu cnpcplus$getMenu();
}
