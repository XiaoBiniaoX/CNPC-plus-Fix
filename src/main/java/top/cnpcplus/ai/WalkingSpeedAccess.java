package top.cnpcplus.ai;

public interface WalkingSpeedAccess {
    float cnpcplus$getWalkingSpeed();
    void cnpcplus$setWalkingSpeed(float speed);

    default float getWalkingSpeedFloat() {
        return cnpcplus$getWalkingSpeed();
    }

    default void setWalkingSpeedFloat(float speed) {
        cnpcplus$setWalkingSpeed(speed);
    }
}
