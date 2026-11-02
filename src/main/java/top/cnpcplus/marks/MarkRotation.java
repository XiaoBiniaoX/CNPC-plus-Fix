package top.cnpcplus.marks;

import net.minecraft.nbt.CompoundTag;

/** Missing keys preserve the native non-spinning mark. */
public interface MarkRotation {
    boolean cnpcplus$horizontal();
    boolean cnpcplus$vertical();
    void cnpcplus$rotation(boolean horizontal, boolean vertical);
    static void read(Object mark, CompoundTag tag) {
        MarkRotation rotation = (MarkRotation)mark;
        rotation.cnpcplus$rotation(tag.getBoolean("CNPCPlusRotateHorizontal"), tag.getBoolean("CNPCPlusRotateVertical"));
    }
    static void write(Object mark, CompoundTag tag) {
        MarkRotation rotation = (MarkRotation)mark;
        tag.putBoolean("CNPCPlusRotateHorizontal", rotation.cnpcplus$horizontal());
        tag.putBoolean("CNPCPlusRotateVertical", rotation.cnpcplus$vertical());
    }
}
