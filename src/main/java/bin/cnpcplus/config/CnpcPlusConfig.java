package bin.cnpcplus.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class CnpcPlusConfig {
    public static String recipeFuzzyMatchRules = "tacz:modern_kinetic_gun|GunId;slashblade:slashblade|";

    private static Configuration config;

    public static void init(File file) {
        config = new Configuration(file);
        load();
    }

    public static void load() {
        if (config == null) return;
        config.load();
        recipeFuzzyMatchRules = config.getString(
                "recipeFuzzyMatchRules",
                "recipe",
                "tacz:modern_kinetic_gun|GunId;slashblade:slashblade|",
                "Format: itemId|field1,field2;itemId2|fieldA  (ignoreDamage=config fuzzy)"
        );
        config.getBoolean(
                "craftingViewEnabled",
                "craftingview",
                true,
                "Show the crafting recipe sidebar on carpentry bench / workbench"
        );
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static Configuration getConfig() {
        return config;
    }

    public static void save() {
        if (config != null) {
            config.save();
        }
    }

    public static String getRecipeFuzzyMatchRules() {
        return recipeFuzzyMatchRules;
    }

    /** Hot-reloadable: reads live from the Configuration object. */
    public static boolean isCraftingViewEnabled() {
        return config == null || config.getBoolean("craftingViewEnabled", "craftingview", true,
                "Show the crafting recipe sidebar on carpentry bench / workbench");
    }
}
