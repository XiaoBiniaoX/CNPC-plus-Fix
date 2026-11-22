package top.cnpcplus.accessor;

public interface RecipeCarpentryOffsetAccessor {
    boolean cnpcplus$isShapeless();
    void cnpcplus$setShapeless(boolean shapeless);
    int cnpcplus$getOffsetX();

    int cnpcplus$getOffsetY();

    boolean cnpcplus$hasSavedOffset();

    void cnpcplus$setOffset(int offsetX, int offsetY, boolean savedOffset);
}
