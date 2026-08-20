package bin.cnpcplus.common;

/** Implemented on the NPC entity to carry the rider input for the current tick. */
public interface IMountControlInput {
    void cnpcplus$setMountInput(float strafe, float forward, boolean jump, boolean sneak);
    float cnpcplus$getMountStrafe();
    float cnpcplus$getMountForward();
    boolean cnpcplus$getMountJump();
    boolean cnpcplus$getMountSneak();
}
