package bin.cnpcplus.smelting;

import java.util.ArrayList;
import java.util.List;

/** Server-sent recipe snapshot; it contains no client-only classes. */
public final class SmeltingClientData {
    private static final List<SmeltingRecipeData> RECIPES = new ArrayList<>();
    private static int selectedId = -1;
    private SmeltingClientData() {}
    public static synchronized void set(List<SmeltingRecipeData> recipes, int selectedId) {
        SmeltingClientData.selectedId = selectedId;
        RECIPES.clear();
        RECIPES.addAll(recipes);
    }
    public static synchronized int selectedId() { return selectedId; }
    public static synchronized List<SmeltingRecipeData> get() { return List.copyOf(RECIPES); }
}
