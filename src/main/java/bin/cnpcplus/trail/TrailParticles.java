package bin.cnpcplus.trail;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Five directional, phase-based projectile trails. Client-only work is gated by Level. */
public final class TrailParticles {
    private TrailParticles() {
    }

    public static void spawn(Entity projectile, int type) {
        if (projectile == null) return;
        Level level = projectile.level();
        if (!level.isClientSide) return;
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 forward = motion.lengthSqr() > 1.0E-7 ? motion.normalize() : new Vec3(0, 0, 1);
        Vec3 side = forward.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-7) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(forward).normalize();
        double x = projectile.getX();
        double y = projectile.getY();
        double z = projectile.getZ();
        float phase = projectile.tickCount * 0.32f;
        switch (type) {
            case 9 -> emberCrown(level, x, y, z, forward, side, up, phase);
            case 10 -> soulHelix(level, x, y, z, side, up, phase);
            case 11 -> prismRing(level, x, y, z, forward, side, up, phase);
            case 12 -> frostPlume(level, x, y, z, forward, side, up, phase);
            case 13 -> starPulse(level, x, y, z, side, up, phase);
            default -> {
            }
        }
    }

    private static void add(Level level, ParticleOptions particle, double x, double y, double z,
                             double dx, double dy, double dz) {
        level.addParticle(particle, x, y, z, dx, dy, dz);
    }

    private static void emberCrown(Level l, double x, double y, double z, Vec3 f, Vec3 s, Vec3 u, float p) {
        for (int i = 0; i < 2; i++) {
            double a = p + i * Math.PI;
            Vec3 o = s.scale(Math.cos(a) * 0.12).add(u.scale(Math.sin(a) * 0.12)).subtract(f.scale(0.12));
            add(l, ParticleTypes.FLAME, x + o.x, y + o.y, z + o.z, -f.x * 0.04, 0.025, -f.z * 0.04);
        }
        if (((int) p & 3) == 0) add(l, ParticleTypes.LAVA, x - f.x * 0.2, y, z - f.z * 0.2, -f.x * 0.06, 0.03, -f.z * 0.06);
    }

    private static void soulHelix(Level l, double x, double y, double z, Vec3 s, Vec3 u, float p) {
        for (int i = 0; i < 2; i++) {
            double a = p * 1.4 + i * Math.PI;
            Vec3 o = s.scale(Math.cos(a) * 0.15).add(u.scale(Math.sin(a) * 0.15));
            add(l, ParticleTypes.SOUL_FIRE_FLAME, x + o.x, y + o.y, z + o.z, -o.x * 0.04, 0.025, -o.z * 0.04);
        }
        add(l, ParticleTypes.SCULK_SOUL, x, y + 0.05, z, 0, 0.035, 0);
    }

    private static void prismRing(Level l, double x, double y, double z, Vec3 f, Vec3 s, Vec3 u, float p) {
        add(l, ParticleTypes.END_ROD, x, y, z, 0, 0.01, 0);
        for (int i = 0; i < 3; i++) {
            double a = p + i * Math.PI * 2.0 / 3.0;
            Vec3 o = s.scale(Math.cos(a) * 0.25).add(u.scale(Math.sin(a) * 0.25));
            add(l, ParticleTypes.END_ROD, x + o.x, y + o.y, z + o.z, -o.x * 0.08, -o.y * 0.08, -o.z * 0.08);
        }
    }

    private static void frostPlume(Level l, double x, double y, double z, Vec3 f, Vec3 s, Vec3 u, float p) {
        double a = p * 0.8;
        Vec3 o = s.scale(Math.cos(a) * 0.14).add(u.scale(Math.sin(a) * 0.14)).subtract(f.scale(0.18));
        add(l, ParticleTypes.SNOWFLAKE, x + o.x, y + o.y, z + o.z, -f.x * 0.02, -0.018, -f.z * 0.02);
        add(l, ParticleTypes.WHITE_ASH, x - f.x * 0.25, y, z - f.z * 0.25, 0, -0.025, 0);
    }

    private static void starPulse(Level l, double x, double y, double z, Vec3 s, Vec3 u, float p) {
        float pulse = 0.08f + 0.06f * (0.5f + 0.5f * Mth.sin(p));
        for (int i = 0; i < 3; i++) {
            double a = p * (i + 1) * 0.7 + i * 2.1;
            Vec3 o = s.scale(Math.cos(a) * pulse).add(u.scale(Math.sin(a) * pulse));
            add(l, ParticleTypes.GLOW, x + o.x, y + o.y, z + o.z, o.x * 0.12, o.y * 0.12, o.z * 0.12);
        }
    }
}
