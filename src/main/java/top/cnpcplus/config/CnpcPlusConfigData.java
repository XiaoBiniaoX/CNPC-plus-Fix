package top.cnpcplus.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端配置（cnpcplus.toml）。
 * 不注册 EventBusSubscriber：见文件末尾说明，监听 ModConfigEvent 会导致玩家连服被踢。
 */
public class CnpcPlusConfigData {

    private static final ForgeConfigSpec CONFIG_SPEC;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("合成视图分类。格式: 名称||名称过滤1,名称过滤2;名称||名称过滤3,名称过滤4");
        CraftingCategories = builder.define("CraftingCategories", "All||");

        builder.comment("配方配置模糊化规则。格式: 物品ID|NBT字段1,NBT字段2;物品ID|。匹配时总是检查物品ID和物品名字。默认包含 TACZ 和拔刀剑。");
        RecipeFuzzyMatchRules = builder.define("RecipeFuzzyMatchRules", "tacz:modern_kinetic_gun|GunId;slashblade:slashblade|");

        builder.comment("木工台/合成台侧栏（合成视图）显示开关（默认true，修改后重新打开界面即生效）");
        CraftingViewEnabled = builder.define("CraftingViewEnabled", true);

        builder.push("对话框");
        builder.comment("对话框中NPC模型的缩放倍数（默认3.0）");
        DialogNpcScale = builder.defineInRange("DialogNpcScale", 3.0, 0.0, 100.0);
        builder.comment("对话框中NPC模型的X位置偏移（默认-80）");
        DialogNpcPosX = builder.defineInRange("DialogNpcPosX", -80, -1000, 1000);
        builder.comment("对话框中NPC模型的Y位置偏移（默认250）");
        DialogNpcPosY = builder.defineInRange("DialogNpcPosY", 250, -1000, 1000);
        builder.comment("对话框选项文字缩放倍数（默认1.0，越大字越大）");
        DialogOptionScale = builder.defineInRange("DialogOptionScale", 1.0, 0.1, 10.0);
        builder.comment("对话框选项区域整体X位置（默认723）");
        DialogOptionPosX = builder.defineInRange("DialogOptionPosX", 723, 0, 2000);
        builder.comment("对话框选项区域整体Y位置（默认220）");
        DialogOptionPosY = builder.defineInRange("DialogOptionPosY", 220, 0, 1000);
        builder.comment("对话框选项之间的额外垂直间距（默认0，正数加大间距）");
        DialogOptionSpacing = builder.defineInRange("DialogOptionSpacing", 0, -50, 50);
        builder.pop();

        builder.push("吟游诗人");
        builder.comment("吟游诗人音乐/唱片机音量倍数（默认1.0，0.0-1.0）");
        BardVolume = builder.defineInRange("BardVolume", 1.0, 0.0, 1.0);
        builder.comment("吟游诗人看门狗时长（秒，默认300=5分钟）：同一首歌超过该时长仍未结束则强制换曲");
        BardWatchdogSeconds = builder.defineInRange("BardWatchdogSeconds", 300, 1, 3600);
        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    public static ForgeConfigSpec getConfig() { return CONFIG_SPEC; }

    // ForgeConfigSpec values
    public static ForgeConfigSpec.ConfigValue<String> CraftingCategories;
    public static ForgeConfigSpec.ConfigValue<String> RecipeFuzzyMatchRules;
    public static ForgeConfigSpec.BooleanValue CraftingViewEnabled;
    public static ForgeConfigSpec.DoubleValue DialogNpcScale;
    public static ForgeConfigSpec.IntValue DialogNpcPosX;
    public static ForgeConfigSpec.IntValue DialogNpcPosY;
    public static ForgeConfigSpec.IntValue DialogOptionPosX;
    public static ForgeConfigSpec.IntValue DialogOptionPosY;
    public static ForgeConfigSpec.DoubleValue DialogOptionScale;
    public static ForgeConfigSpec.IntValue DialogOptionSpacing;
    public static ForgeConfigSpec.DoubleValue BardVolume;
    public static ForgeConfigSpec.IntValue BardWatchdogSeconds;

    /*
     * 同 CnpcPlusServerConfig：刻意不再监听 ModConfigEvent 去调 save()。
     *
     * 这里虽然是 CLIENT 类型配置，但监听的 ModConfigEvent 是全局事件，任何 mod 的配置
     * 触发它时本方法都会被调用（客户端日志实证：Listeners 列表里 index 1 是
     * CnpcPlusServerConfig、index 2 就是本类，两个都挂在同一条事件链上）。
     * 握手期同步 SERVER 配置时一并被触发 → ModConfig.save() 把内存态 SimpleCommentedConfig
     * 强转 CommentedFileConfig → ClassCastException → 玩家被判「无效的数据包」踢出。
     * ForgeConfigSpec 自己会在文件变更时回写，这个 save() 从来就是多余的。
     */
}
