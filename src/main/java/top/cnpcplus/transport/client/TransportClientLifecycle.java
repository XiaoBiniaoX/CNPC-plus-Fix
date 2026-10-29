package top.cnpcplus.transport.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 换服务器/退出存档时释放传送贴图和未收齐的图片，避免跨世界缓存串用。 */
@EventBusSubscriber(modid = "cnpcplus", value = Dist.CLIENT)
public final class TransportClientLifecycle {
    private TransportClientLifecycle() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { TransportClientCache.clear(); }
}
