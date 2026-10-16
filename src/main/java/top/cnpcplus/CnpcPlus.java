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
        // 渲染优化配置（蓝图预览/轮廓缓存/阴影上限），独立文件避免与主配置撞车。
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, top.cnpcplus.perf.Config.SPEC, "cnpcplus-perf.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CnpcPlusServerConfig.getConfig(), "cnpcplus-server.toml");
        // Better_Modlist / Forge 标准配置界面扩展点：只在物理客户端注册；
        // ConfigScreenHandler 是 net.minecraftforge.client 类，专用服务器不加载。
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (mc, parent) -> new top.cnpcplus.config.client.CnpcPlusConfigScreen(parent)));
        }
        ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, (level, helper) -> {});
        top.cnpcplus.craftingview.network.PacketHandler.init();
        ModRegistry.register();
        TriggerPacketHandler.init();
        NpcInvPagePacketHandler.init();
        TraderPagePacketHandler.init();
        FollowerPacketHandler.init();
        SmeltingMenus.register();
        SmeltingPacketHandler.init();
        top.cnpcplus.smithing.SmithingMenus.register();
        top.cnpcplus.smithing.SmithingSerializers.register();
        top.cnpcplus.smithing.network.SmithingPacketHandler.init();
        // 阶段231 可视化自定义铁砧配方：不注册 Serializers（不进 RecipeManager），
        // 生效层=AnvilCraftHandler 挂 Forge AnvilUpdateEvent（下方 EVENT_BUS.register）
        top.cnpcplus.anvil.AnvilMenus.register();
        top.cnpcplus.anvil.network.AnvilPacketHandler.init();
        // 阶段232 可视化自定义掉落物配方：不注册 Serializers（不进 RecipeManager），
        // 生效层=DropCraftHandler 挂 EntityJoinLevel/Tick/Leave（服务端物品落地触发，下方注册）
        top.cnpcplus.drop.DropMenus.register();
        top.cnpcplus.drop.network.DropPacketHandler.init();
        LinkedPacketHandler.init();
        top.cnpcplus.villagertrade.network.VillagerEditPacketHandler.init();
        // 熔炼配方注入时机：世界加载时 RecipeManager 的首次 apply 发生在 MinecraftServer 实例创建之前，
        // 那时读不到服务端、也就注入不了。所以在服务端启动完成后再补注入一次（/reload 由 mixin 覆盖）。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStartedEvent e) -> {
                    top.cnpcplus.smelting.SmeltingRecipeManager.injectAll(e.getServer().getRecipeManager());
                    top.cnpcplus.smithing.SmithingRecipeManager.injectAll(e.getServer().getRecipeManager());
                    java.io.File dataDir = new java.io.File(e.getServer().getServerDirectory(), "data/cnpcplus");
                    top.cnpcplus.villagertrade.TradeDataStore.setStoragePath(
                        new java.io.File(dataDir, "villager_trades.dat"));
                });
        // 玩家数据防回退（问题2）：Clone 拷 NBT + 错峰自动保存
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(top.cnpcplus.playerdata.PlayerDataEvents.class);
        // 阶段231 铁砧配方生效层：AnvilUpdateEvent 双端 fire（客户端本地预览 + 服务端权威执行）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(top.cnpcplus.anvil.AnvilCraftHandler.class);
        // 阶段232 掉落物配方生效层：仅服务端逻辑（ItemToss/EntityLeave + LevelTick pending驱动，
        // 玩家丢出物品落地触发合成；LevelTick 入口 pending 空集 O(1) 短路，无每实体扫描）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(top.cnpcplus.drop.DropCraftHandler.class);
        // 233d 掉落物粒子特效运行时（LevelTick END 队列驱动；空队列 O(1) 短路）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(top.cnpcplus.drop.fx.runtime.DropFxEngine.class);
        // /cnpcplus playerdata load <player>（对齐 1.21.1，权限 level 4）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.RegisterCommandsEvent e) ->
                        top.cnpcplus.command.CmdPlayerData.register(e.getDispatcher()));
        // S1：PacketNpcUpdate 实体未注册时缓存补放（客户端 tick；类仅在 Dist.CLIENT 分支内被引用，专用服务器不会加载）
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    (net.minecraftforge.event.TickEvent.ClientTickEvent ce) -> {
                        if (ce.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                            top.cnpcplus.sync.client.PendingNpcUpdateStore.tick();
                        }
                    });
        }
        // 服务端停止时清掉配方缓存：单人切换存档后必须重读，否则会把上个存档的配方带进新世界
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppedEvent e) -> {
                    // 先等异步落盘线程把队列写完，再清缓存（防最后一批修改丢失）
                    top.cnpcplus.smithing.SmithingRecipeRegistry.awaitDiskPending();
                    top.cnpcplus.anvil.AnvilRecipeRegistry.awaitDiskPending();
                    top.cnpcplus.drop.DropRecipeRegistry.awaitDiskPending();
                    top.cnpcplus.smelting.SmeltingRecipeRegistry.clearCache();
                    top.cnpcplus.smithing.SmithingRecipeRegistry.clearCache();
                    top.cnpcplus.anvil.AnvilRecipeRegistry.clearCache();
                    top.cnpcplus.drop.DropRecipeRegistry.clearCache();
                });
    }
}
