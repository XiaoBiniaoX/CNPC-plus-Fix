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
        config.getBoolean("returnHomeSmoothArrival", "ai", true,
                "Walk the last steps back to the start point instead of pausing and teleporting when almost there");
        config.getFloat("returnHomeArrivalTolerance", "ai", 3.0F, 0.5F, 32.0F,
                "Horizontal distance (blocks) within which the return-to-start teleport is suppressed");
        config.getInt("returnHomeTimeoutSeconds", "ai", 30, 1, 3600,
                "Seconds before an npc that cannot reach its start point is teleported there (vanilla: 30)");
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

    /**
     * 返回起点时，快到了是否走完最后一段而不是停顿 + 瞬移。
     *
     * 关掉即恢复原版行为，便于出问题时对照。
     */
    public static boolean isReturnHomeSmoothArrival() {
        return config == null || config.getBoolean("returnHomeSmoothArrival", "ai", true,
                "Walk the last steps back to the start point instead of pausing and teleporting when almost there");
    }

    /**
     * 抑制返回起点瞬移的水平距离容差（格）。
     *
     * 超出这个距离仍然瞬移 —— 那种情况是真卡住（卡墙里、掉洞里、地形不可达），
     * 保底机制不能拆。
     */
    public static float getReturnHomeArrivalTolerance() {
        return config == null ? 3.0F : config.getFloat("returnHomeArrivalTolerance", "ai",
                3.0F, 0.5F, 32.0F,
                "Horizontal distance (blocks) within which the return-to-start teleport is suppressed");
    }

    /**
     * 返回起点的超时瞬移时间（秒）。
     *
     * 原版是 {@code EntityAIReturn.MaxTotalTicks = 600} ticks，即 **30 秒**
     * （哈基彬印象里的「1 分钟左右」偏大）。这里保持原版默认值，
     * 只是让它可以在 cfg 里改。
     */
    public static int getReturnHomeTimeoutSeconds() {
        return config == null ? 30 : config.getInt("returnHomeTimeoutSeconds", "ai",
                30, 1, 3600,
                "Seconds before an npc that cannot reach its start point is teleported there (vanilla: 30)");
    }

    /** 超时瞬移的 tick 阈值，直接换算自 {@link #getReturnHomeTimeoutSeconds()}。 */
    public static int getReturnHomeTimeoutTicks() {
        return getReturnHomeTimeoutSeconds() * 20;
    }
}
