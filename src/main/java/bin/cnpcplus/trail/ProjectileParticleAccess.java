package bin.cnpcplus.trail;

import net.minecraft.network.syncher.EntityDataAccessor;
import noppes.npcs.entity.EntityProjectile;

import java.lang.reflect.Field;

/** Reads CNPC's existing synchronized projectile particle selector. */
public final class ProjectileParticleAccess {
    private static EntityDataAccessor<Integer> accessor;

    private ProjectileParticleAccess() {
    }

    @SuppressWarnings("unchecked")
    private static synchronized EntityDataAccessor<Integer> get() {
        if (accessor != null) return accessor;
        try {
            Field field = EntityProjectile.class.getDeclaredField("Particle");
            field.setAccessible(true);
            accessor = (EntityDataAccessor<Integer>) field.get(null);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            accessor = null;
        }
        return accessor;
    }

    public static int getParticle(EntityProjectile projectile) {
        EntityDataAccessor<Integer> key = get();
        if (key == null || projectile == null) return 0;
        try {
            Integer value = projectile.getEntityData().get(key);
            return value == null ? 0 : value;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
