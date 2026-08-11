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
        config.getFloat("BardVolume", "bard", 1.0F, 0.0F, 1.0F,
                "Bard music volume multiplier (0.0-1.0)");
        config.getInt("BardWatchdogSeconds", "bard", 300, 1, 3600,
                "Watchdog seconds: force-switch a bard song that plays longer than this");
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

    /** Bard music volume multiplier (0.0-1.0), applies to bard playlist playback. */
    public static float getBardVolume() {
        return config == null ? 1.0F : config.getFloat("BardVolume", "bard", 1.0F, 0.0F, 1.0F,
                "Bard music volume multiplier (0.0-1.0)");
    }

    /** Watchdog: force-switch a bard song that plays longer than this (seconds). */
    public static int getBardWatchdogSeconds() {
        return config == null ? 300 : config.getInt("BardWatchdogSeconds", "bard", 300, 1, 3600,
                "Watchdog seconds: force-switch a bard song that plays longer than this");
    }
}
