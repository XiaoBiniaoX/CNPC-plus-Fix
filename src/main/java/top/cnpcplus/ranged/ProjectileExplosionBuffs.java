package top.cnpcplus.ranged;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;

public final class ProjectileExplosionBuffs {
    public static final ThreadLocal<Impact> ACTIVE = new ThreadLocal<>();

    public static final class Impact {
        public Explosion explosion;
        public final Set<LivingEntity> damaged = new HashSet<>();
    }

    /** Forge can cancel LivingHurtEvent after the caller has already accepted the hit. */
    public static boolean didDamage(LivingEntity entity, DamageSource source, float amount) {
        float health = entity.getHealth();
        float absorption = entity.getAbsorptionAmount();
        return entity.hurt(source, amount)
                && (entity.getHealth() < health || entity.getAbsorptionAmount() < absorption);
    }
}
