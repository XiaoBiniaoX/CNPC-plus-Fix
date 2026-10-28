package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.ProjectileSpinKeys;
import net.minecraft.client.renderer.GlStateManager;
import noppes.npcs.client.renderer.RenderProjectile;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderProjectile.class, remap = false)
public abstract class MixinRenderProjectileSpin {
    @Inject(method = "doRenderProjectile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;func_179137_b(DDD)V"), require = 1)
    private void cnpcplus$rotateConfiguredAxes(EntityProjectile projectile, double x, double y, double z,
                                               float yaw, float partialTicks, CallbackInfo ci) {
        if (!ProjectileSpinKeys.configured(projectile)) return;
        GlStateManager.rotate(ProjectileSpinKeys.angle(projectile, 0, partialTicks), 1, 0, 0);
        GlStateManager.rotate(ProjectileSpinKeys.angle(projectile, 1, partialTicks), 0, 1, 0);
        GlStateManager.rotate(ProjectileSpinKeys.angle(projectile, 2, partialTicks), 0, 0, 1);
    }
}
