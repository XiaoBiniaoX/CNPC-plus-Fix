package bin.cnpcplus.marks;

import net.minecraft.nbt.NBTTagCompound;

/** Stored on each native mark, not on the NPC or a shared renderer. */
public interface MarkRotation {
    boolean cnpcplus$horizontal();
    boolean cnpcplus$vertical();
    void cnpcplus$rotation(boolean horizontal, boolean vertical);
    static void read(Object mark, NBTTagCompound tag) {
        ((MarkRotation) mark).cnpcplus$rotation(tag.getBoolean("CNPCPlusRotateHorizontal"), tag.getBoolean("CNPCPlusRotateVertical"));
    }
    static void write(Object mark, NBTTagCompound tag) {
        MarkRotation rotation = (MarkRotation) mark;
        tag.setBoolean("CNPCPlusRotateHorizontal", rotation.cnpcplus$horizontal());
        tag.setBoolean("CNPCPlusRotateVertical", rotation.cnpcplus$vertical());
    }
}
