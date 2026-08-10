package bin.cnpcplus.mixin;

import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * EntityProjectile.tick() pulls xRot 20% toward the motion-derived pitch every tick
 * (Projectile.lerpRotation = wrap + Mth.lerp(0.2F, current, target)), which cancels the spin
 * decrement (xRot -= spin*speed) applied right after - the projectile converges to a fixed
 * tilt instead of visibly rotating. While isRotating(), skip the lerp setXRot so the spin
 * accumulates continuously. Kept on both sides so server angle packets don't yank the client
 * back. ordinal 1 = the lerp call site (0 = first-tick init, 2 = the spin decrement).
 */
@Mixin(value = EntityProjectile.class, remap = false)
public class EntityProjectileSpinFixMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setXRot(F)V",
                    ordinal = 1))
    private void cnpcplus$skipLerpWhileRotating(EntityProjectile self, float value) {
        if (!self.isRotating()) {
            self.setXRot(value);
        }
    }
}
