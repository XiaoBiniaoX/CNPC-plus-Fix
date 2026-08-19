package top.cnpcplus.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.trail.ProjectileParticleAccess;
import top.cnpcplus.trail.TrailParticles;

/**
 * B1: 弹射物粒子轨迹增强。
 * 在 EntityProjectile.tick(m_8119_) 的粒子生成点拦截 `level.addParticle(...)`。
 * 轨迹粒子恒以速度 0,0,0 生成：速度全 0 → 走增强轨迹；入水气泡等带实际速度保持原逻辑。
 * 类型 9-13（B1 追加的 5 种）生成动画感复合粒子。
 */
@Mixin(value = EntityProjectile.class, remap = false)
public class MixinEntityProjectileTrail {

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7106_(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void cnpcplus$spawnTrail(Level level, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
            int id = ProjectileParticleAccess.getParticle((EntityProjectile) (Object) this);
            // 只增强 B1 追加的 9-13；0-8 保持原逻辑（含 Crit=8），避免原本轨迹失效
            if (id >= 9 && id <= 13) {
                TrailParticles.spawn(level, id, x, y, z);
                return;
            }
        }
        level.addParticle(type, x, y, z, dx, dy, dz);
    }
}