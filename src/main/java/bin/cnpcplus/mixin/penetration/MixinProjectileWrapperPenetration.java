package bin.cnpcplus.mixin.penetration;

import bin.cnpcplus.common.IProjectilePenetration;
import noppes.npcs.api.wrapper.ProjectileWrapper;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Script API bridge for projectile penetration.
 * ProjectileWrapper.entity is inherited from EntityWrapper, so @Shadow is not
 * reliable here; the public getMCEntity() accessor is used instead.
 */
@Mixin(value = ProjectileWrapper.class, remap = false)
public class MixinProjectileWrapperPenetration implements IProjectilePenetration {
    @Override
    public int cnpcplus$getPenetration() {
        Object entity = ((ProjectileWrapper) (Object) this).getMCEntity();
        return entity instanceof IProjectilePenetration
                ? ((IProjectilePenetration) entity).cnpcplus$getPenetration() : 0;
    }

    @Override
    public void cnpcplus$setPenetration(int value) {
        Object entity = ((ProjectileWrapper) (Object) this).getMCEntity();
        if (entity instanceof IProjectilePenetration) {
            ((IProjectilePenetration) entity).cnpcplus$setPenetration(value);
        }
    }
}
