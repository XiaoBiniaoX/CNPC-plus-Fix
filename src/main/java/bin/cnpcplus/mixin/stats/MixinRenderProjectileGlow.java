package bin.cnpcplus.mixin.stats;

import net.minecraft.client.renderer.OpenGlHelper;
import noppes.npcs.client.renderer.RenderProjectile;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply the synchronized full-bright value to CNPC's special 2D arrow renderer too. */
@Mixin(value = RenderProjectile.class, remap = false)
public abstract class MixinRenderProjectileGlow {
    @Unique private float cnpcplus$lightX;
    @Unique private float cnpcplus$lightY;
    @Unique private boolean cnpcplus$restoreLightmap;

    @Inject(method = "doRenderProjectile", at = @At("HEAD"), require = 1)
    private void cnpcplus$fullBrightArrow(EntityProjectile projectile, double x, double y, double z,
                                         float yaw, float partialTicks, CallbackInfo ci) {
        cnpcplus$restoreLightmap = projectile.glows();
        if (!cnpcplus$restoreLightmap) return;
        cnpcplus$lightX = OpenGlHelper.lastBrightnessX;
        cnpcplus$lightY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
    }

    @Inject(method = "doRenderProjectile", at = @At("RETURN"), require = 1)
    private void cnpcplus$restoreLightmap(EntityProjectile projectile, double x, double y, double z,
                                          float yaw, float partialTicks, CallbackInfo ci) {
        if (!cnpcplus$restoreLightmap) return;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, cnpcplus$lightX, cnpcplus$lightY);
        cnpcplus$restoreLightmap = false;
    }
}
