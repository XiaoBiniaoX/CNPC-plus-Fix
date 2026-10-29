package bin.cnpcplus.mixin;

import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.ranged.RangedSpinStore;

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
                     target = "Lnoppes/npcs/entity/EntityProjectile;setXRot(F)V",
                     ordinal = 1), require = 1)
    private void cnpcplus$skipLerpWhileRotating(EntityProjectile self, float value) {
        // 自定义XYZ沿用来源的运动朝向；只有原生自旋保留已有的插值抑制。
        var data = self.getEntityData();
        if (!self.isRotating() || data.get(RangedSpinStore.SPIN_X) != 0.0f
                || data.get(RangedSpinStore.SPIN_Y) != 0.0f || data.get(RangedSpinStore.SPIN_Z) != 0.0f) {
            self.setXRot(value);
        }
    }
}
