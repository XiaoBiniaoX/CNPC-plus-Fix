package bin.cnpcplus.mixin;

import noppes.npcs.client.renderer.RenderProjectile;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 renders projectile scale as size/10*2, the 1.21.1 port dropped the *2 - the
 * projectile renders half the size it should. Returning size*2 from getSize() restores
 * the 1.20.1 behaviour (scale = size/10*2) without touching the rest of render().
 */
@Mixin(value = RenderProjectile.class, remap = false)
public class RenderProjectileMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityProjectile;getSize()I"))
    private int cnpcplus$doubleProjectileScale(EntityProjectile projectile) {
        return projectile.getSize() * 2;
    }
}
