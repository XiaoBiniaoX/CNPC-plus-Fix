package bin.cnpcplus.mixin.model;

import net.minecraft.client.renderer.GlStateManager;
import noppes.npcs.client.layer.LayerHead;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LayerHead.class, remap = false)
public abstract class MixinLayerHeadTransform {
    @Inject(method = {"renderSnout", "renderBeard", "renderHair", "renderMohawk", "renderHorns", "renderEars"}, at = @At("HEAD"), require = 6)
    private void cnpcplus$begin(float scale, CallbackInfo ci) { GlStateManager.pushMatrix(); }
    @Inject(method = {"renderSnout", "renderBeard", "renderHair", "renderMohawk", "renderHorns", "renderEars"}, at = @At("RETURN"), require = 6)
    private void cnpcplus$end(float scale, CallbackInfo ci) { GlStateManager.popMatrix(); }
}
