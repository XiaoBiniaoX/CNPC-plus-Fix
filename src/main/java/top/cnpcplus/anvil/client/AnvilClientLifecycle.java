package top.cnpcplus.anvil.client;

import bin.cnpcplus.CnpcPlus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 换服/退出世界时清空服务端配方快照，防止下一台服务器沿用旧列表。 */
@EventBusSubscriber(modid = CnpcPlus.MODID, value = Dist.CLIENT)
public final class AnvilClientLifecycle {
    private AnvilClientLifecycle() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        AnvilClientData.set(java.util.List.of());
    }
}
