package bin.cnpcplus.common;

import noppes.npcs.entity.EntityProjectile;
import java.util.Map;
import java.util.WeakHashMap;

public final class ProjectileSpinKeys {

    public static final String[] NBT_KEYS = {"CNPCPlusSpinSpeed", "CNPCPlusSpinSpeedY", "CNPCPlusSpinSpeedZ"};
    private static final Map<EntityProjectile, float[]> ANGLES = new WeakHashMap<>();

    public static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(-1000.0F, Math.min(1000.0F, value)) : 0.0F;
    }

    public static boolean configured(EntityProjectile projectile) {
        for (int axis = 0; axis < 3; axis++) {
            if (((IRangedSpinAccess)projectile).cnpcplus$getSpinSpeed(axis) != 0.0F) return true;
        }
        return false;
    }

    public static void advance(EntityProjectile projectile) {
        float[] angles = ANGLES.computeIfAbsent(projectile, ignored -> new float[6]);
        for (int axis = 0; axis < 3; axis++) {
            angles[axis + 3] = angles[axis] % 360.0F;
            angles[axis] = angles[axis + 3] + ((IRangedSpinAccess)projectile).cnpcplus$getSpinSpeed(axis) * projectile.getSpeed();
        }
    }

    public static float angle(EntityProjectile projectile, int axis, float partialTicks) {
        float[] angles = ANGLES.get(projectile);
        return angles == null ? 0.0F : angles[axis + 3]
                + (angles[axis] - angles[axis + 3]) * Math.max(0.0F, Math.min(1.0F, partialTicks));
    }

    private ProjectileSpinKeys() {
    }
}
