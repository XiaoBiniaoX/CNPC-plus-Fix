package top.cnpcplus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Safe subset of npcfix-main; target filtering remains vanilla/CNPC-compatible by default. */
public final class NpcAiOptimizerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue TARGET_SEARCH_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue STAGGER_TARGET_SEARCHES;
    public static final ModConfigSpec.IntValue MAX_EFFECTIVE_AGGRO_RANGE;
    public static final ModConfigSpec.BooleanValue UPDATE_EVENT_WHEN_SCRIPTS_DISABLED;
    public static final ModConfigSpec.IntValue UPDATE_EVENT_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue COLLIDE_SCAN_WHEN_SCRIPTS_DISABLED;
    public static final ModConfigSpec.IntValue COLLIDE_SCAN_INTERVAL_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("npcfix_ai");
        ENABLED = builder.comment("启用 NPC AI 性能优化；关闭后恢复原始频率。").define("enabled", true);
        TARGET_SEARCH_INTERVAL_TICKS = builder.comment("已停用：仅保留旧配置兼容，不再限制索敌启动频率。").defineInRange("target_search_interval_ticks", 20, 4, 200);
        STAGGER_TARGET_SEARCHES = builder.comment("已停用：仅保留旧配置兼容，不再影响索敌。").define("stagger_target_searches", true);
        MAX_EFFECTIVE_AGGRO_RANGE = builder.comment("有效仇恨范围上限；0 表示使用 NPC 原始 AggroRange。只做距离快速排除，不改变阵营规则。").defineInRange("max_effective_aggro_range", 0, 0, 128);
        builder.pop();
        builder.push("npcfix_events");
        UPDATE_EVENT_WHEN_SCRIPTS_DISABLED = builder.comment("脚本关闭时仍保留 UpdateEvent，默认 true 以避免改变敌对/监听行为。").define("update_event_when_scripts_disabled", true);
        UPDATE_EVENT_INTERVAL_TICKS = builder.comment("NPC UpdateEvent 间隔（tick），默认10保持原始频率。").defineInRange("update_event_interval_ticks", 10, 10, 200);
        COLLIDE_SCAN_WHEN_SCRIPTS_DISABLED = builder.comment("脚本关闭时仍保留碰撞扫描，默认 true 以避免改变事件行为。").define("collide_scan_when_scripts_disabled", true);
        COLLIDE_SCAN_INTERVAL_TICKS = builder.comment("NPC 碰撞扫描间隔（tick），默认4保持原始频率。").defineInRange("collide_scan_interval_ticks", 4, 4, 200);
        builder.pop();
        SPEC = builder.build();
    }

    private NpcAiOptimizerConfig() {}
}
