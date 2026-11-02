package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.SafeEntityPreview;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryEntityPreview {
    @Inject(method = "renderEntityInInventory", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$safe(GuiGraphics g, int x, int y, int size, Quaternionf pose, Quaternionf camera, LivingEntity entity, CallbackInfo ci) {
        SafeEntityPreview.inventory(g, x, y, size, pose, camera, entity);
        ci.cancel();
    }
}
