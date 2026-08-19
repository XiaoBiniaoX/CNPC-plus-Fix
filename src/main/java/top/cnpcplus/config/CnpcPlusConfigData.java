package top.cnpcplus.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import top.cnpcplus.CnpcPlus;

@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
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

        builder.push("熔炼配方界面布局");
        builder.comment("「可视化自定义熔炼配方」界面的整体偏移（单位像素）。热修改后重新打开界面即生效。");
        builder.comment("其余元素坐标（槽位/火焰/箭头/开关/输入框/按钮）已按满意布局硬编码，不再提供配置。");
        builder.comment("整个界面一起移动：背景板、顶部菜单条、全部按钮/输入框、三个槽位。Y 增大 = 整体往下（默认20，在默认窗口下露出顶部菜单）");
        SmeltingGuiOffsetX = builder.defineInRange("SmeltingGuiOffsetX", 0, -300, 300);
        SmeltingGuiOffsetY = builder.defineInRange("SmeltingGuiOffsetY", 52, -300, 300);
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
    public static ForgeConfigSpec.IntValue SmeltingGuiOffsetX;
    public static ForgeConfigSpec.IntValue SmeltingGuiOffsetY;

    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CONFIG_SPEC) {
            event.getConfig().save();
        }
    }
}
