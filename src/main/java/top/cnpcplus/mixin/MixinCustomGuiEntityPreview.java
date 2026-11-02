package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.gui.custom.components.CustomGuiEntityDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.SafeEntityPreview;

@Mixin(value = CustomGuiEntityDisplay.class, remap = false)
public abstract class MixinCustomGuiEntityPreview {
    @Inject(method = "drawEntity(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/Entity;IIFIIIFFZZ)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$safe(GuiGraphics g, Entity entity, int x, int y, float zoom, int rotation,
            int mx, int my, float left, float top, boolean follow, boolean riders, CallbackInfo ci) {
        SafeEntityPreview.nativeDisplay(g, entity, x, y, zoom, rotation, mx, my, left, top, follow, riders);
        ci.cancel();
    }
}
