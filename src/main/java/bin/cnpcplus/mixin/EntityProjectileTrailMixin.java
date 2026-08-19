package bin.cnpcplus.mixin;

import bin.cnpcplus.trail.ProjectileParticleAccess;
import bin.cnpcplus.trail.TrailParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityProjectile.class, remap = false)
public class EntityProjectileTrailMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", ordinal = 1), remap = false, require = 1)
    private void cnpcplus$spawnCustomTrail(Level level, ParticleOptions original, double x, double y, double z,
                                           double dx, double dy, double dz) {
        EntityProjectile projectile = (EntityProjectile) (Object) this;
        int id = ProjectileParticleAccess.getParticle(projectile);
        if (id >= 9 && id <= 13) {
            TrailParticles.spawn(projectile, id);
        } else {
            level.addParticle(original, x, y, z, dx, dy, dz);
        }
    }
}
