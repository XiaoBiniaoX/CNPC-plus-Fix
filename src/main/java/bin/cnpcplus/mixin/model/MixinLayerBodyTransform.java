package bin.cnpcplus.mixin.model;

import net.minecraft.client.renderer.GlStateManager;
import noppes.npcs.client.layer.LayerBody;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LayerBody.class, remap = false)
public abstract class MixinLayerBodyTransform {
    @Inject(method = {"renderSkirt", "renderWings", "renderFin", "renderBreasts"}, at = @At("HEAD"), require = 4)
    private void cnpcplus$begin(float scale, CallbackInfo ci) { GlStateManager.pushMatrix(); }
    @Inject(method = {"renderSkirt", "renderWings", "renderFin", "renderBreasts"}, at = @At("RETURN"), require = 4)
    private void cnpcplus$end(float scale, CallbackInfo ci) { GlStateManager.popMatrix(); }
}
