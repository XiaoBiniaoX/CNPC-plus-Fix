package top.cnpcplus.smithing.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.cnpcplus.CnpcPlus;
import top.cnpcplus.smithing.SmithingMenus;

@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SmithingClientRegistry {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(SmithingMenus.SMITHING_RECIPES.get(), GuiNpcSmithingRecipes::new));
        // 断线/退出存档时清空客户端配方缓存：缓存铁律是「非空不请求」，
        // 不清的话换存档/换服务器会拿上个世界的列表当真（显示旧配方、选中发出去被服务端按 id 拒绝）。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
                        SmithingClientData.set(java.util.List.of()));
    }
}
