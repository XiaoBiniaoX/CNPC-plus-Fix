package top.cnpcplus.client;

import bin.cnpcplus.CnpcPlus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import top.cnpcplus.villagertrade.EditMode;
import top.cnpcplus.villagertrade.TradeDataStore;

@EventBusSubscriber(modid = CnpcPlus.MODID, value = Dist.CLIENT)
public final class VillagerTradeClientEvents {
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TradeDataStore.clearClientCache();
        EditMode.active = false;
    }
}
