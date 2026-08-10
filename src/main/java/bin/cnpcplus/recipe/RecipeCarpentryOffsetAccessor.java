package bin.cnpcplus.recipe;

public interface RecipeCarpentryOffsetAccessor {
    int cnpcplusGetOffsetX();

    int cnpcplusGetOffsetY();

    boolean cnpcplusHasSavedOffset();

    void cnpcplusSetOffset(int offsetX, int offsetY, boolean saved);
}
