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
        config.getInt("traderMarketCacheMode", "performance", 1, 0, 2,
                "Trader market file cache: 0=off (vanilla), 1=validate by timestamp+size, 2=memory only");
        config.getInt("farmerScanRange", "performance", 8, 2, 16,
                "Farmer job block scan range (vanilla: 16). Volume scales with the cube of this value");
        config.getInt("farmerScanSlices", "performance", 4, 1, 16,
                "Split the farmer block scan across this many ticks to avoid a single-frame spike");
        config.getInt("npcHurtScanRange", "performance", 16, 4, 32,
                "Horizontal range for the faction-defend scan when an npc is hurt (vanilla: 32)");
        config.getInt("npcHurtScanCooldown", "performance", 20, 0, 200,
                "Minimum ticks between two faction-defend scans on the same npc, limits AoE chain storms");
        config.getBoolean("npcRetargetCloser", "performance", true,
                "Let an npc already in combat switch to a closer attacker instead of locking onto the first target");
        config.getBoolean("keepGlobalEntityRadius", "performance", true,
                "Do not raise the global World.MAX_ENTITY_RADIUS for big npcs; compensate locally instead");
        config.getInt("getsAttackedScanRange", "performance", 12, 4, 16,
                "Range for the faction getsAttacked mob scan (vanilla: 16)");
        config.getInt("borderTickInterval", "performance", 4, 1, 20,
                "Border block scan interval in ticks (vanilla: 1, i.e. every tick)");
        config.getBoolean("lazyItemStackWrapper", "performance", true,
                "Allocate item wrapper sub-objects on first use instead of for every ItemStack");
        config.getBoolean("skipUnusedCollideEvents", "performance", true,
                "Skip building npc collide events when no script and no listener consumes them");
        config.getBoolean("pruneCombatAggressors", "performance", true,
                "Drop invalid entries from the npc aggressor table instead of keeping them forever");
        config.getBoolean("urlSkinDiskCache", "performance", true,
                "Cache downloaded url skins on disk and stop retrying forever after repeated failures");
        config.getBoolean("ponySkinFixDeadBranch", "performance", false,
                "Also fix the discarded color comparison in the pony skin check (option C, changes pegasus/unicorn detection)");
        config.getBoolean("skirtDistanceLod", "performance", true,
                "Reduce skirt segments for distant npcs");
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

    // ------------------------------------------------------------ 性能优化开关
    //
    // 全部集中在 performance 分类下，每一项都能单独关掉退回原版行为。
    // 这是刻意的：性能改动一旦出问题，用户改 cfg 就能撤回，不必回退整个版本。

    /**
     * 商人市场文件缓存模式（性能问题 6）。
     *
     * 0 = 关闭（原版行为，每次交互读盘 + 解析）
     * 1 = 时间戳 + 文件长度校验（默认）
     * 2 = 纯内存不校验（最快，但外部改文件不生效）
     *
     * 哈基彬要求「留接口可以随时撤回」，这就是那个接口。
     */
    public static int getTraderMarketCacheMode() {
        return config == null ? 1 : config.getInt("traderMarketCacheMode", "performance", 1, 0, 2,
                "Trader market file cache: 0=off (vanilla), 1=validate by timestamp+size, 2=memory only");
    }

    /** 农夫扫描半径（性能问题 2）。原版 16，体积按立方增长。 */
    public static int getFarmerScanRange() {
        return config == null ? 8 : config.getInt("farmerScanRange", "performance", 8, 2, 16,
                "Farmer job block scan range (vanilla: 16). Volume scales with the cube of this value");
    }

    /** 农夫扫描分摊到多少 tick（性能问题 2 的分摊要求）。 */
    public static int getFarmerScanSlices() {
        return config == null ? 4 : config.getInt("farmerScanSlices", "performance", 4, 1, 16,
                "Split the farmer block scan across this many ticks to avoid a single-frame spike");
    }

    /** NPC 受伤后阵营求援扫描的水平半径（性能问题 4）。原版 32。 */
    public static int getNpcHurtScanRange() {
        return config == null ? 16 : config.getInt("npcHurtScanRange", "performance", 16, 4, 32,
                "Horizontal range for the faction-defend scan when an npc is hurt (vanilla: 32)");
    }

    /** 同一 NPC 两次求援扫描的最小间隔 tick（性能问题 4，防 AOE 连锁）。 */
    public static int getNpcHurtScanCooldown() {
        return config == null ? 20 : config.getInt("npcHurtScanCooldown", "performance", 20, 0, 200,
                "Minimum ticks between two faction-defend scans on the same npc, limits AoE chain storms");
    }

    /** 战斗中是否允许换到更近的攻击者（性能问题 4 的仇恨死逻辑）。 */
    public static boolean isNpcRetargetCloserEnabled() {
        return config == null || config.getBoolean("npcRetargetCloser", "performance", true,
                "Let an npc already in combat switch to a closer attacker instead of locking onto the first target");
    }

    /** 是否阻止大体积 NPC 抬高全局 World.MAX_ENTITY_RADIUS（性能问题 5）。 */
    public static boolean isKeepGlobalEntityRadius() {
        return config == null || config.getBoolean("keepGlobalEntityRadius", "performance", true,
                "Do not raise the global World.MAX_ENTITY_RADIUS for big npcs; compensate locally instead");
    }

    /** faction getsAttacked 扫描半径（性能问题 10）。原版 16。 */
    public static int getGetsAttackedScanRange() {
        return config == null ? 12 : config.getInt("getsAttackedScanRange", "performance", 12, 4, 16,
                "Range for the faction getsAttacked mob scan (vanilla: 16)");
    }

    /** 边界方块扫描间隔 tick（性能问题 12）。原版每 tick。 */
    public static int getBorderTickInterval() {
        return config == null ? 4 : config.getInt("borderTickInterval", "performance", 4, 1, 20,
                "Border block scan interval in ticks (vanilla: 1, i.e. every tick)");
    }

    /** ItemStack wrapper 子对象是否懒分配（性能问题 11）。 */
    public static boolean isLazyItemStackWrapper() {
        return config == null || config.getBoolean("lazyItemStackWrapper", "performance", true,
                "Allocate item wrapper sub-objects on first use instead of for every ItemStack");
    }

    /** 无脚本无监听时是否跳过碰撞事件构建（性能问题 13）。 */
    public static boolean isSkipUnusedCollideEvents() {
        return config == null || config.getBoolean("skipUnusedCollideEvents", "performance", true,
                "Skip building npc collide events when no script and no listener consumes them");
    }

    /** 是否清理 CombatHandler 里的失效仇恨条目（性能问题 14）。 */
    public static boolean isPruneCombatAggressors() {
        return config == null || config.getBoolean("pruneCombatAggressors", "performance", true,
                "Drop invalid entries from the npc aggressor table instead of keeping them forever");
    }

    /** URL 皮肤是否启用落盘缓存与失败退避（性能问题 15）。 */
    public static boolean isUrlSkinDiskCache() {
        return config == null || config.getBoolean("urlSkinDiskCache", "performance", true,
                "Cache downloaded url skins on disk and stop retrying forever after repeated failures");
    }

    /**
     * 是否顺带修复 pony 皮肤检查里那个被丢弃的颜色比较（性能问题 16 的方案 C）。
     *
     * 默认关：那处 `Color.equals` + `ifeq` 跳到紧邻下一条指令，比较结果完全丢弃，
     * 属原作者的源码 bug。修了会改变 pegasus/unicorn 的判定行为，
     * 而我无法确认原意，所以交给哈基彬决定 —— 这就是他要的「口子」。
     */
    public static boolean isPonySkinFixDeadBranch() {
        return config != null && config.getBoolean("ponySkinFixDeadBranch", "performance", false,
                "Also fix the discarded color comparison in the pony skin check (option C, changes pegasus/unicorn detection)");
    }

    /** 裙摆是否按距离降段数（性能问题 17）。 */
    public static boolean isSkirtDistanceLod() {
        return config == null || config.getBoolean("skirtDistanceLod", "performance", true,
                "Reduce skirt segments for distant npcs");
    }
}
