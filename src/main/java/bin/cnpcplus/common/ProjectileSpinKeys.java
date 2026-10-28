package bin.cnpcplus.common;

import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import noppes.npcs.entity.EntityProjectile;
import java.util.Map;
import java.util.WeakHashMap;

public final class ProjectileSpinKeys {

    // Defer allocation until entityInit, after CNPC's own static data keys exist.
    private static final class Parameters {
        private static final DataParameter<Float> X = EntityDataManager.createKey(EntityProjectile.class, DataSerializers.FLOAT);
        private static final DataParameter<Float> Y = EntityDataManager.createKey(EntityProjectile.class, DataSerializers.FLOAT);
        private static final DataParameter<Float> Z = EntityDataManager.createKey(EntityProjectile.class, DataSerializers.FLOAT);
    }
    public static final String[] NBT_KEYS = {"CNPCPlusSpinSpeed", "CNPCPlusSpinSpeedY", "CNPCPlusSpinSpeedZ"};
    private static final Map<EntityProjectile, float[]> ANGLES = new WeakHashMap<>();

    public static DataParameter<Float> key(int axis) {
        return axis == 0 ? Parameters.X : axis == 1 ? Parameters.Y : Parameters.Z;
    }

    public static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(-1000.0F, Math.min(1000.0F, value)) : 0.0F;
    }

    public static boolean configured(EntityProjectile projectile) {
        for (int axis = 0; axis < 3; axis++) {
            if (projectile.getDataManager().get(key(axis)) != 0.0F) return true;
        }
        return false;
    }

    public static void advance(EntityProjectile projectile) {
        float[] angles = ANGLES.computeIfAbsent(projectile, ignored -> new float[6]);
        for (int axis = 0; axis < 3; axis++) {
            angles[axis + 3] = angles[axis] % 360.0F;
            angles[axis] = angles[axis + 3] + projectile.getDataManager().get(key(axis)) * projectile.getSpeed();
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
