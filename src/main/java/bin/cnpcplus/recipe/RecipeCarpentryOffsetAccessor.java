package bin.cnpcplus.recipe;

public interface RecipeCarpentryOffsetAccessor {
    boolean cnpcplus$isShapeless();
    void cnpcplus$setShapeless(boolean shapeless);
    int cnpcplusGetOffsetX();

    int cnpcplusGetOffsetY();

    boolean cnpcplusHasSavedOffset();

    void cnpcplusSetOffset(int offsetX, int offsetY, boolean saved);
}
