package bin.cnpcplus.model;

import net.minecraft.nbt.NBTTagCompound;

/** Attachment-local scale and offset, in model pixels; NBT names match 1.20.1. */
public final class PartTransform {
    private static final String KEY = "CNPCPlusTransform";
    private static final String[] AXES = {"ScaleX", "ScaleY", "ScaleZ", "OffsetX", "OffsetY", "OffsetZ"};
    private final float[] values = {1, 1, 1, 0, 0, 0};

    public float get(int axis) { return values[axis]; }
    public void set(int axis, float value) {
        values[axis] = Float.isFinite(value) ? Math.max(axis < 3 ? .05F : -64, Math.min(axis < 3 ? 8 : 64, value)) : axis < 3 ? 1 : 0;
    }
    public boolean isIdentity() {
        for (int i = 0; i < 6; i++) if (values[i] != (i < 3 ? 1 : 0)) return false;
        return true;
    }
    public void read(NBTTagCompound part) {
        NBTTagCompound tag = part.getCompoundTag(KEY);
        for (int i = 0; i < 6; i++) set(i, tag.hasKey(AXES[i], 99) ? tag.getFloat(AXES[i]) : i < 3 ? 1 : 0);
    }
    public void write(NBTTagCompound part) {
        if (isIdentity()) { part.removeTag(KEY); return; }
        NBTTagCompound tag = new NBTTagCompound();
        for (int i = 0; i < 6; i++) tag.setFloat(AXES[i], values[i]);
        part.setTag(KEY, tag);
    }
    public interface Access { PartTransform cnpcplus$partTransform(); }
}
