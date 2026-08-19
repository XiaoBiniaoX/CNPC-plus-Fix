package top.cnpcplus.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import top.cnpcplus.CnpcPlus;

/**
 * 服务端配置（cnpcplus-server.toml）。服务端逻辑（随从 AI、熔炼配方等）读取。
 */
@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CnpcPlusServerConfig {

    private static final ForgeConfigSpec CONFIG_SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("随从");
        builder.comment("随从/雇佣兵距离玩家超过该格数（平方距离开方）后强制传送到玩家身边，避免被困在坑洞/墙壁中无法跟随（默认12，范围1-64）");
        FollowerTeleportRange = builder.defineInRange("FollowerTeleportRange", 12, 1, 64);
        builder.pop();

        builder.push("自定义熔炼");
        builder.comment("熔炼配方文件路径下的数据是否在服务端加载时重新注册到 Minecraft RecipeManager（默认true）");
        SmeltingRegisterOnServer = builder.define("SmeltingRegisterOnServer", true);
        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    public static ForgeConfigSpec getConfig() { return CONFIG_SPEC; }

    public static ForgeConfigSpec.IntValue FollowerTeleportRange;
    public static ForgeConfigSpec.BooleanValue SmeltingRegisterOnServer;

    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CONFIG_SPEC) {
            event.getConfig().save();
        }
    }
}
