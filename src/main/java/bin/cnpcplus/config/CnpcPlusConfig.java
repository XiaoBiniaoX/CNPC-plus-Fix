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
                "NPC「返回起点」的超时瞬移时间（秒，默认60）",
                "超过该时长仍未回到起点则直接瞬移过去，用于地形被封死等走不回去的情况",
                "原版硬编码为 30 秒且在快到起点时会因反复重试提前瞬移，本模组已修好末段抖动"
            )
            .defineInRange("returnToStartTimeoutSeconds", 60, 1, 3600);

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

    public static final ModConfigSpec SPEC = BUILDER.build();
}
