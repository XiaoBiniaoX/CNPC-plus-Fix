package bin.cnpcplus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CnpcPlusConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> DIALOG_TEXT_COLOR = BUILDER
            .comment(
                "对话文本基础十六进制颜色 (RGB)",
                "例: E0E0E0 / #FFFFFF / FFAA00",
                "若对话内使用了&颜色码，则优先使用&颜色"
            )
            .define("dialogTextColor", "E0E0E0");

    public static final ModConfigSpec.ConfigValue<String> DIALOG_TEXT_FORMAT = BUILDER
            .comment(
                "对话文本默认&格式前缀 (可叠加样式)",
                "颜色: &0-&9 &a-&f | 样式: &l粗体 &o斜体 &n下划线 &m删除线 &k乱码 &r重置",
                "例: &l &o &6&l  空=不附加格式"
            )
            .define("dialogTextFormat", "");

    public static final ModConfigSpec.ConfigValue<String> DIALOG_OPTION_COLOR = BUILDER
            .comment(
                "对话选项基础十六进制颜色 (RGB)",
                "例: E0E0E0 / #FFFFFF / 55FF55",
                "若选项内使用了&颜色码，则优先使用&颜色"
            )
            .define("dialogOptionColor", "E0E0E0");

    public static final ModConfigSpec.ConfigValue<String> DIALOG_OPTION_FORMAT = BUILDER
            .comment(
                "对话选项默认&格式前缀 (可叠加样式)",
                "颜色: &0-&9 &a-&f | 样式: &l粗体 &o斜体 &n下划线 &m删除线 &k乱码 &r重置",
                "例: &7 &b&o  空=不附加格式"
            )
            .define("dialogOptionFormat", "");

    public static final ModConfigSpec.ConfigValue<String> RECIPE_FUZZY_MATCH_RULES = BUILDER
            .comment(
                "配方配置模糊化规则（沿用 1.20.1 CNPCplus）",
                "格式: 物品ID|NBT字段1,NBT字段2;物品ID|",
                "当配方勾选「配置模糊化」(原 ignoreDamage) 时生效",
                "匹配时总是检查物品ID和显示名称；再检查列出的 CustomData 字符串字段",
                "默认包含 TACZ 与拔刀剑"
            )
            .define("recipeFuzzyMatchRules", "tacz:modern_kinetic_gun|GunId;slashblade:slashblade|");

    public static final ModConfigSpec.BooleanValue CRAFTING_VIEW_ENABLED = BUILDER
            .comment(
                "木工台/合成台侧栏（合成视图）显示开关",
                "默认 true；false 时隐藏侧栏",
                "热加载：修改保存后即时生效，无需重启"
            )
            .define("craftingViewEnabled", true);

    public static final ModConfigSpec.BooleanValue NPC_NAMES_OBSCURED = BUILDER
            .comment(
                "NPC 名字是否会被其他实体和方块遮挡",
                "默认 true；false 时保留原来的穿墙显示"
            )
            .define("npcNamesObscured", true);

    public static final ModConfigSpec.DoubleValue BARD_VOLUME = BUILDER
            .comment(
                "吟游诗人音乐/唱片机音量倍数（默认1.0，0.0-1.0）",
                "与游戏「音乐」音量滑块相互独立，专门控制吟游诗人播放的曲子"
            )
            .defineInRange("bardVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.IntValue BARD_WATCHDOG_SECONDS = BUILDER
            .comment(
                "吟游诗人看门狗时长（秒，默认300=5分钟）",
                "同一首歌超过该时长仍未结束则强制换曲"
            )
            .defineInRange("bardWatchdogSeconds", 300, 1, 3600);

    public static final ModConfigSpec.IntValue RETURN_START_TIMEOUT_SECONDS = BUILDER
            .comment(
                "NPC「返回起点」的超时瞬移时间（秒，默认30）",
                "超过该时长仍未回到起点则直接瞬移过去，用于地形被封死等走不回去的情况",
                "默认值与原版一致（EntityAIReturn.MaxTotalTicks = 600 tick = 30 秒），仅让它可配",
                "原版还会在快到起点时因反复重试提前瞬移，那个问题本模组已单独修好"
            )
            .defineInRange("returnToStartTimeoutSeconds", 30, 1, 3600);

    public static final ModConfigSpec.BooleanValue RETURN_START_SMOOTH_ARRIVAL = BUILDER
            .comment(
                "修复 NPC「返回起点」快到时反复停顿并瞬移/起跳（默认 true）",
                "关掉即恢复原版行为，便于出问题时对照"
            )
            .define("returnToStartSmoothArrival", true);

    public static final ModConfigSpec.DoubleValue RETURN_START_ARRIVAL_TOLERANCE = BUILDER
            .comment(
                "「返回起点」判定已到位的水平容差（格，默认3.0）",
                "原版要求 X/Z 误差都在 ±0.2 内，而寻路到点精度约 0.45 格，根本达不到，",
                "于是每次都被当成卡住：停 10 tick、重新寻路、反复抖动，最后直接瞬移。",
                "NPC 碰撞宽度 0.6、寻路节点整格对齐，3 格能覆盖「路径已走完但差最后一两步」的全部情形"
            )
            .defineInRange("returnToStartArrivalTolerance", 3.0, 0.5, 32.0);

    public static final ModConfigSpec.BooleanValue CORPSE_NO_COLLISION = BUILDER
            .comment(
                "固定点位重生的 NPC 死亡后，尸体是否去掉碰撞箱（默认 true）",
                "开启后尸体不再挡路、不挡准星、不吃箭；复活后碰撞箱自动恢复",
                "不影响 spawnCycle 为「死后消失」的 NPC，它们本来就会直接移除"
            )
            .define("corpseNoCollision", true);

    public static final ModConfigSpec.IntValue SMELTING_GUI_OFFSET_X = BUILDER
            .comment("可视化自定义熔炼配方界面整体X偏移")
            .defineInRange("smeltingGuiOffsetX", 0, -300, 300);

    public static final ModConfigSpec.IntValue SMELTING_GUI_OFFSET_Y = BUILDER
            .comment("可视化自定义熔炼配方界面整体Y偏移；1.20.1参考默认20")
            .defineInRange("smeltingGuiOffsetY", 20, -300, 300);

    public static final ModConfigSpec.BooleanValue REAL_ARMOR_ENABLED = BUILDER
            .comment(
                "真实护甲（默认 false）",
                "开启后 NPC 身上的原版盔甲按原版公式真实减伤，并按 伤害/4 每件消耗耐久",
                "关闭时与原版一致：护甲只存不生效（伙伴 role 6 除外，它自带算法）",
                "摔落/虚空等 BYPASSES_ARMOR 伤害不受影响；热改热生效"
            )
            .define("realArmorEnabled", false);

    public static final ModConfigSpec.BooleanValue REAL_ARMOR_OVERRIDE_COMPANION = BUILDER
            .comment(
                "真实护甲是否覆盖「伙伴」(role 6)（默认 false）",
                "伙伴自带 (25-护甲值)/25 的简化护甲算法，",
                "false = 伙伴保持自带算法不变；true = 伙伴也走真实护甲公式"
            )
            .define("realArmorOverrideCompanion", false);

    public static final ModConfigSpec.DoubleValue SHADOW_RADIUS_MAX = BUILDER
            .comment(
                "NPC 阴影半径上限，单位：格（默认999）",
                "999 保持普通 CNPC 原阴影尺寸；主动调小才会改变大体型 NPC 阴影",
                "0 关闭投影，2 为旧优化默认值",
                "只影响影子，不改变模型、碰撞箱、伤害或动画"
            )
            .defineInRange("shadowRadiusMax", 999.0, 0.0, 999.0);

    public static final ModConfigSpec.BooleanValue OUTLINE_CACHE_ENABLED = BUILDER
            .comment(
                "共享 CNPC 原版生成的 2D 轮廓（默认 true）",
                "关闭后走原版实例缓存",
                "缺图与越界 UV 仍由原版处理，不因失败而隐藏模型；F3+T 重载时清理显存缓存"
            )
            .define("outlineCacheEnabled", true);

    public static final ModConfigSpec.IntValue OUTLINE_CACHE_ENTRIES = BUILDER
            .comment(
                "共享轮廓最多保留多少项（不是 NPC 数量，默认2048）",
                "达到上限淘汰最久未用项，被淘汰的轮廓下次需要时会重新生成，不会让模型缺失"
            )
            .defineInRange("outlineCacheEntries", 2048, 64, 16384);

    public static final ModConfigSpec.BooleanValue BLUEPRINT_CACHE_ENABLED = BUILDER
            .comment(
                "缓存建筑方块蓝图预览（默认 true）",
                "关闭可回到 CNPC 原版预览绘制，便于兼容排查",
                "只影响预览，不影响实际建筑操作"
            )
            .define("blueprintCacheEnabled", true);

    public static final ModConfigSpec.DoubleValue BLUEPRINT_BUDGET_MILLIS = BUILDER
            .comment(
                "每帧用于构建预览的软时间预算（毫秒，默认2）",
                "调小减轻瞬时卡顿，代价是完整预览出现得更慢，不会减少最终预览方块",
                "单个模组模型计算或一次 GPU 上传无法中断，故不是绝对的帧时间上限"
            )
            .defineInRange("blueprintBudgetMillis", 2.0, 0.25, 20.0);

    public static final ModConfigSpec.IntValue BLUEPRINT_MAX_BLOCKS_PER_FRAME = BUILDER
            .comment(
                "每帧最多处理的预览方块数，与时间预算同时生效；包含空气（默认2048）",
                "未完成的数据留到下一帧，不截断，不调整实际建造速度"
            )
            .defineInRange("blueprintMaxBlocksPerFrame", 2048, 16, 8192);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
