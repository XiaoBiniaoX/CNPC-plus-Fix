package top.cnpcplus;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.world.ForgeChunkManager;
import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.follower.network.FollowerPacketHandler;
import top.cnpcplus.invpage.network.NpcInvPagePacketHandler;
import top.cnpcplus.questtrigger.ModRegistry;
import top.cnpcplus.questtrigger.network.TriggerPacketHandler;
import top.cnpcplus.linked.network.LinkedPacketHandler;
import top.cnpcplus.smelting.SmeltingMenus;
import top.cnpcplus.smelting.network.SmeltingPacketHandler;
import top.cnpcplus.trader.network.TraderPagePacketHandler;

@Mod(CnpcPlus.MOD_ID)
public class CnpcPlus {
    public static final String MOD_ID = "cnpcplus";

    public CnpcPlus() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CnpcPlusConfigData.getConfig(), "cnpcplus.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CnpcPlusServerConfig.getConfig(), "cnpcplus-server.toml");
        ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, (level, helper) -> {});
        top.cnpcplus.craftingview.network.PacketHandler.init();
        ModRegistry.register();
        TriggerPacketHandler.init();
        NpcInvPagePacketHandler.init();
        TraderPagePacketHandler.init();
        FollowerPacketHandler.init();
        SmeltingMenus.register();
        SmeltingPacketHandler.init();
        LinkedPacketHandler.init();
        // 熔炼配方注入时机：世界加载时 RecipeManager 的首次 apply 发生在 MinecraftServer 实例创建之前，
        // 那时读不到服务端、也就注入不了。所以在服务端启动完成后再补注入一次（/reload 由 mixin 覆盖）。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStartedEvent e) -> {
                    top.cnpcplus.smelting.SmeltingRecipeManager.injectAll(e.getServer().getRecipeManager());
                });
        // 服务端停止时清掉配方缓存：单人切换存档后必须重读，否则会把上个存档的配方带进新世界
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppedEvent e) ->
                        top.cnpcplus.smelting.SmeltingRecipeRegistry.clearCache());
    }
}
