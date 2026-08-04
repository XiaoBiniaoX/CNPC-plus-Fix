package top.cnpcplus.craftingview;

import top.cnpcplus.config.CnpcPlusConfigData;

import java.util.*;

public class Config {

    private static final List<CategoryDefinition> categoriesMutable = new ArrayList<>();
    public static final List<CategoryDefinition> categories = Collections.unmodifiableList(categoriesMutable);

    public static void reload() {
        categoriesMutable.clear();
        String raw = CnpcPlusConfigData.CraftingCategories.get();
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split(";")) {
            CategoryDefinition def = parse(part);
            if (def != null) categoriesMutable.add(def);
        }
    }

    private static CategoryDefinition parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("\\|", -1);
        String name = parts.length > 0 ? parts[0].trim() : "";
        if (name.isEmpty()) return null;

        List<String> names = new ArrayList<>();
        if (parts.length > 2 && !parts[2].isBlank()) {
            for (String s : parts[2].split(",")) {
                String n = s.trim();
                if (!n.isEmpty()) names.add(n.toLowerCase());
            }
        }

        return new CategoryDefinition(name, names);
    }

    public static class CategoryDefinition {
        public final String name;
        public final List<String> recipeNames;
        public final Set<String> recipeNamesSet;

        public CategoryDefinition(String name, List<String> recipeNames) {
            this.name = name;
            this.recipeNames = Collections.unmodifiableList(recipeNames);
            this.recipeNamesSet = Collections.unmodifiableSet(new HashSet<>(recipeNames));
        }
    }
}
