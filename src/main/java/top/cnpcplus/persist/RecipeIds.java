package top.cnpcplus.persist;

import net.minecraft.resources.ResourceLocation;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.Locale;
import java.util.UUID;

public final class RecipeIds {

    private RecipeIds() {}

    /** Stable unique id, independent of display name. */
    public static ResourceLocation fresh() {
        String path = "r_" + UUID.randomUUID().toString().replace("-", "");
        return new ResourceLocation("customnpcs", path);
    }

    public static ResourceLocation uniquePath(String preferred) {
        String base = sanitize(preferred);
        if (base.isEmpty()) base = "recipe";
        ResourceLocation id = new ResourceLocation("customnpcs", base);
        int n = 0;
        while (taken(id)) {
            n++;
            id = new ResourceLocation("customnpcs", base + "_" + n);
        }
        return id;
    }

    public static boolean taken(ResourceLocation id) {
        if (id == null) return false;
        if (PersistedRecipeStore.contains(id)) return true;
        RecipeController ctrl = RecipeController.instance;
        if (ctrl == null) return false;
        return ctrl.getRecipe(id) != null;
    }

    public static boolean nameTaken(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        RecipeController ctrl = RecipeController.instance;
        if (ctrl != null) {
            for (RecipeCarpentry r : ctrl.globalRecipes.values()) {
                if (r != null && r.name != null && r.name.toLowerCase(Locale.ROOT).equals(lower)) return true;
            }
            for (RecipeCarpentry r : ctrl.anvilRecipes.values()) {
                if (r != null && r.name != null && r.name.toLowerCase(Locale.ROOT).equals(lower)) return true;
            }
        }
        for (RecipeCarpentry r : PersistedRecipeStore.list()) {
            if (r != null && r.name != null && r.name.toLowerCase(Locale.ROOT).equals(lower)) return true;
        }
        return false;
    }

    public static String uniqueDisplayName(String preferred) {
        String name = preferred == null || preferred.isEmpty() ? "new" : preferred;
        while (nameTaken(name)) {
            name = name + "_";
        }
        return name;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        while (s.startsWith("_")) s = s.substring(1);
        if (s.length() > 64) s = s.substring(0, 64);
        return s;
    }
}
