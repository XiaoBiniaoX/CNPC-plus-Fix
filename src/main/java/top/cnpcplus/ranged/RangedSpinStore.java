package top.cnpcplus.ranged;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;

import java.util.Map;
import java.util.WeakHashMap;

/** Storage, persistence and projectile sync accessors for the three spin axes. */
public final class RangedSpinStore {

    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    /** X keeps the original key so existing single-axis configs continue to load. */
    public static final String NBT_KEY_X = "CNPCPlusSpinSpeed";
    public static final String NBT_KEY_Y = "CNPCPlusSpinSpeedY";
    public static final String NBT_KEY_Z = "CNPCPlusSpinSpeedZ";
    public static final float MAX_SPIN = 1000.0f;

    public static final EntityDataAccessor<Float> SPIN_X =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> SPIN_Y =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> SPIN_Z =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);

    private static final String[] NBT_KEYS = {NBT_KEY_X, NBT_KEY_Y, NBT_KEY_Z};
    private static final Map<DataRanged, float[]> SPINS = new WeakHashMap<>();
    /** Client-side continuous angles: current XYZ followed by previous XYZ. */
    private static final Map<EntityProjectile, float[]> ANGLES = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private RangedSpinStore() {
    }

    public static float get(DataRanged ranged, int axis) {
        if (ranged == null || !validAxis(axis)) return 0.0f;
        synchronized (LOCK) {
            float[] values = SPINS.get(ranged);
            return values == null ? 0.0f : values[axis];
        }
    }

    public static void put(DataRanged ranged, int axis, float factor) {
        if (ranged == null || !validAxis(axis)) return;
        if (!Float.isFinite(factor)) factor = 0.0f;
        factor = Math.max(-MAX_SPIN, Math.min(MAX_SPIN, factor));
        synchronized (LOCK) {
            float[] values = SPINS.computeIfAbsent(ranged, ignored -> new float[3]);
            values[axis] = factor;
            if (values[0] == 0.0f && values[1] == 0.0f && values[2] == 0.0f) {
                SPINS.remove(ranged);
            }
        }
    }

    public static void clear(DataRanged ranged) {
        if (ranged == null) return;
        synchronized (LOCK) {
            SPINS.remove(ranged);
        }
    }

    public static void write(DataRanged ranged, CompoundTag compound) {
        if (compound == null) return;
        float x = get(ranged, AXIS_X);
        float y = get(ranged, AXIS_Y);
        float z = get(ranged, AXIS_Z);
        writeAxis(compound, AXIS_X, x);
        writeAxis(compound, AXIS_Y, y);
        writeAxis(compound, AXIS_Z, z);
    }

    public static void read(DataRanged ranged, CompoundTag compound) {
        if (ranged == null || compound == null) return;
        float x = readAxis(compound, AXIS_X);
        float y = readAxis(compound, AXIS_Y);
        float z = readAxis(compound, AXIS_Z);
        put(ranged, AXIS_X, x);
        put(ranged, AXIS_Y, y);
        put(ranged, AXIS_Z, z);
    }

    public static void advanceAngles(EntityProjectile projectile, float x, float y, float z) {
        if (projectile == null || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
        synchronized (LOCK) {
            float[] angles = ANGLES.computeIfAbsent(projectile, ignored -> new float[6]);
            angles[3] = angles[0];
            angles[4] = angles[1];
            angles[5] = angles[2];
            angles[0] += x;
            angles[1] += y;
            angles[2] += z;
        }
    }

    public static float angleOf(EntityProjectile projectile, int axis, float partialTicks) {
        if (projectile == null || !validAxis(axis)) return 0.0f;
        synchronized (LOCK) {
            float[] angles = ANGLES.get(projectile);
            if (angles == null) return 0.0f;
            float partial = Math.max(0.0f, Math.min(1.0f, partialTicks));
            return angles[axis + 3] + (angles[axis] - angles[axis + 3]) * partial;
        }
    }

    private static void writeAxis(CompoundTag compound, int axis, float value) {
        String key = NBT_KEYS[axis];
        if (value == 0.0f) compound.remove(key);
        else compound.putFloat(key, value);
    }

    private static float readAxis(CompoundTag compound, int axis) {
        String key = NBT_KEYS[axis];
        return compound.contains(key, Tag.TAG_ANY_NUMERIC) ? compound.getFloat(key) : 0.0f;
    }

    private static boolean validAxis(int axis) {
        return axis >= AXIS_X && axis <= AXIS_Z;
    }
}
