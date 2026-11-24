package top.cnpcplus.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** The part's scale and displacement along its attachment axes, in model pixels. */
public final class PartTransform {
    private static final String KEY = "CNPCPlusTransform";
    private static final String[] AXES = {"ScaleX", "ScaleY", "ScaleZ", "OffsetX", "OffsetY", "OffsetZ"};
    private final float[] values = {1, 1, 1, 0, 0, 0};

    public float get(int axis) { return values[axis]; }

    public void set(int axis, float value) {
        float min = axis < 3 ? 0.05f : -64;
        float max = axis < 3 ? 8 : 64;
        values[axis] = Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : axis < 3 ? 1 : 0;
    }

    public boolean isIdentity() {
        for (int i = 0; i < 6; i++) if (values[i] != (i < 3 ? 1 : 0)) return false;
        return true;
    }

    public void read(CompoundTag part) {
        CompoundTag tag = part.getCompound(KEY);
        for (int i = 0; i < 6; i++) set(i, tag.contains(AXES[i], Tag.TAG_ANY_NUMERIC) ? tag.getFloat(AXES[i]) : i < 3 ? 1 : 0);
    }

    public void write(CompoundTag part) {
        if (isIdentity()) return;
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < 6; i++) tag.putFloat(AXES[i], values[i]);
        part.put(KEY, tag);
    }

    public interface Access {
        PartTransform cnpcplus$partTransform();
    }
}
