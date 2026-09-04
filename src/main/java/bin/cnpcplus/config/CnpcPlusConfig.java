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
        config.getBoolean("interactPassthrough", "interact", true,
                "Let held items (bow, food, potion) still work when the crosshair is on an NPC that has nothing to interact with");
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

    public static void setBardVolume(float volume) {
        if (config == null) return;
        float value = Math.max(0.0F, Math.min(1.0F, volume));
        config.get("bard", "BardVolume", 1.0D, "Bard music volume multiplier (0.0-1.0)").set(value);
        config.save();
    }

    /** Watchdog: force-switch a bard song that plays longer than this (seconds). */
    public static int getBardWatchdogSeconds() {
        return config == null ? 300 : config.getInt("BardWatchdogSeconds", "bard", 300, 1, 3600,
                "Watchdog seconds: force-switch a bard song that plays longer than this");
    }

    /**
     * 准星对着「没有任何交互内容」的 NPC 时，是否放行手持物品的右键使用。
     *
     * 开关留在这里是为了万一与其他改右键的 mod 冲突时能直接关掉，
     * 而不必回退整个版本。
     */
    public static boolean isInteractPassthroughEnabled() {
        return config == null || config.getBoolean("interactPassthrough", "interact", true,
                "Let held items (bow, food, potion) still work when the crosshair is on an NPC that has nothing to interact with");
    }
}
