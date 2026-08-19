package top.cnpcplus.trail;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * 弹射物粒子轨迹生成（B1 增强：动画感复合粒子）。
 * 对追加的 5 种轨迹(id 9-13)生成多个带随机速度的粒子，形成流动/闪烁动画，
 * 而非单一静态粒子。每次 tick 调用一次。
 */
public final class TrailParticles {

    private TrailParticles() {}

    public static void spawn(Level level, int type, double x, double y, double z) {
        if (level == null || level.isClientSide() == false) return;
        RandomSource rand = level.random;
        switch (type) {
            case 9 -> flame(level, rand, x, y, z);
            case 10 -> soul(level, rand, x, y, z);
            case 11 -> endRod(level, rand, x, y, z);
            case 12 -> snow(level, rand, x, y, z);
            case 13 -> glow(level, rand, x, y, z);
            default -> { }
        }
    }

    private static void add(Level level, ParticleOptions p, double x, double y, double z,
                            double dx, double dy, double dz) {
        level.addParticle(p, x, y, z, dx, dy, dz);
    }

    private static void flame(Level level, RandomSource r, double x, double y, double z) {
        // 火焰拖尾：橙红火苗 + 轻微烟雾上浮
        add(level, ParticleTypes.FLAME, x, y, z, (r.nextFloat() - 0.5) * 0.05, 0.02 + r.nextFloat() * 0.03, (r.nextFloat() - 0.5) * 0.05);
        add(level, ParticleTypes.FLAME, x, y, z, (r.nextFloat() - 0.5) * 0.08, 0.01, (r.nextFloat() - 0.5) * 0.08);
        if (r.nextInt(3) == 0) {
            add(level, ParticleTypes.SMOKE, x, y, z, 0, 0.05, 0);
        }
    }

    private static void soul(Level level, RandomSource r, double x, double y, double z) {
        // 灵魂火：蓝绿灵魂火 + 飘散光点上浮
        add(level, ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, 0.04, 0);
        add(level, ParticleTypes.SCULK_SOUL, x, y, z, (r.nextFloat() - 0.5) * 0.1, 0.05 + r.nextFloat() * 0.05, (r.nextFloat() - 0.5) * 0.1);
    }

    private static void endRod(Level level, RandomSource r, double x, double y, double z) {
        // 末地烛：亮光 + 环绕电火花闪烁
        add(level, ParticleTypes.END_ROD, x, y, z, 0, 0.005, 0);
        if (r.nextInt(2) == 0) {
            double a = r.nextDouble() * Math.PI * 2;
            double rad = 0.35;
            add(level, ParticleTypes.END_ROD,
                    x + Math.cos(a) * rad, y + 0.25, z + Math.sin(a) * rad,
                    -Math.cos(a) * 0.12, 0, -Math.sin(a) * 0.12);
        }
    }

    private static void snow(Level level, RandomSource r, double x, double y, double z) {
        // 雪花：晶莹雪花 + 白灰细屑缓降
        add(level, ParticleTypes.SNOWFLAKE, x, y, z, (r.nextFloat() - 0.5) * 0.04, -0.02 - r.nextFloat() * 0.03, (r.nextFloat() - 0.5) * 0.04);
        if (r.nextInt(2) == 0) {
            add(level, ParticleTypes.WHITE_ASH, x, y, z, (r.nextFloat() - 0.5) * 0.06, -0.04, (r.nextFloat() - 0.5) * 0.06);
        }
    }

    private static void glow(Level level, RandomSource r, double x, double y, double z) {
        // 萤火：发光点漂散 + 少量小光点
        add(level, ParticleTypes.GLOW, x, y, z, (r.nextFloat() - 0.5) * 0.15, (r.nextFloat() - 0.5) * 0.15, (r.nextFloat() - 0.5) * 0.15);
        if (r.nextInt(2) == 0) {
            add(level, ParticleTypes.GLOW, x, y, z, (r.nextFloat() - 0.5) * 0.25, (r.nextFloat() - 0.5) * 0.25, (r.nextFloat() - 0.5) * 0.25);
        }
    }
}