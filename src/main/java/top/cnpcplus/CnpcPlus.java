package top.cnpcplus;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.world.ForgeChunkManager;
import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.invpage.network.NpcInvPagePacketHandler;
import top.cnpcplus.questtrigger.ModRegistry;
import top.cnpcplus.questtrigger.network.TriggerPacketHandler;
import top.cnpcplus.trader.network.TraderPagePacketHandler;

@Mod(CnpcPlus.MOD_ID)
public class CnpcPlus {
    public static final String MOD_ID = "cnpcplus";

    public CnpcPlus() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CnpcPlusConfigData.getConfig(), "cnpcplus.toml");
        ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, (level, helper) -> {});
        top.cnpcplus.craftingview.network.PacketHandler.init();
        ModRegistry.register();
        TriggerPacketHandler.init();
        NpcInvPagePacketHandler.init();
        TraderPagePacketHandler.init();
    }
}
