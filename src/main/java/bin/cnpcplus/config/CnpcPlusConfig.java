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
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static String getRecipeFuzzyMatchRules() {
        return recipeFuzzyMatchRules;
    }
}
