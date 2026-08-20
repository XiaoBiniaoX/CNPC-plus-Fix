package bin.cnpcplus.common;

/** Implemented on DataAI so the mount control toggle persists with the NPC AI data. */
public interface IMountControlData {
    boolean cnpcplus$getMountControl();
    void cnpcplus$setMountControl(boolean enabled);
}
