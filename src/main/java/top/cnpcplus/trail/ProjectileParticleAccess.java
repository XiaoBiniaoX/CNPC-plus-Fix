package top.cnpcplus.trail;

import net.minecraft.network.syncher.EntityDataAccessor;
import noppes.npcs.entity.EntityProjectile;

import java.lang.reflect.Field;

/** 读取 EntityProjectile 的 private static Particle 数据访问器（反射，缓存引用）。 */
public final class ProjectileParticleAccess {
    private static EntityDataAccessor<Integer> accessor;

    private ProjectileParticleAccess() {}

    public static synchronized EntityDataAccessor<Integer> get() {
        if (accessor != null) return accessor;
        try {
            Field f = EntityProjectile.class.getDeclaredField("Particle");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Integer> acc = (EntityDataAccessor<Integer>) f.get(null);
            accessor = acc;
        } catch (Exception e) {
            accessor = null;
        }
        return accessor;
    }

    public static int getParticle(EntityProjectile proj) {
        EntityDataAccessor<Integer> acc = get();
        if (acc == null) return 0;
        try {
            Integer v = proj.getEntityData().get(acc);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }
}