package top.cnpcplus.drop.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.cnpcplus.CnpcPlus;
import top.cnpcplus.drop.DropMenus;

/**
 * 掉落物配方界面的客户端注册（232b 补漏：漏掉时 MenuScreens 构造器表无
 * cnpcplus:drop_recipes → 日志 WARN "Failed to create screen"、点按钮打不开，
 * GUI 类甚至没被实例化）。照抄 AnvilClientRegistry。
 */
@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DropClientRegistry {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(DropMenus.DROP_RECIPES.get(), GuiDropRecipes::new));
        // 断线/退出存档时清空客户端配方缓存：缓存铁律是「非空不请求」，
        // 不清的话换存档/换服务器会拿上个世界的列表当真。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                        DropClientData.set(java.util.List.of()));
    }
}
