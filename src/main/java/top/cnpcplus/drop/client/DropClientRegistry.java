package top.cnpcplus.drop.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import bin.cnpcplus.CnpcPlus;

/**
 * 掉落物配方界面的客户端注册（232b 补漏：漏掉时 MenuScreens 构造器表无
 * cnpcplus:drop_recipes → 日志 WARN "Failed to create screen"、点按钮打不开，
 * GUI 类甚至没被实例化）。照抄 AnvilClientRegistry。
 */
@EventBusSubscriber(modid = CnpcPlus.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class DropClientRegistry {

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DropClientData.set(java.util.List.of());
    }
}
