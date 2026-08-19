package bin.cnpcplus;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import bin.cnpcplus.smelting.SmeltingMenus;
import bin.cnpcplus.smelting.client.SmeltingScreen;

@Mod(value = CnpcPlus.MODID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
@EventBusSubscriber(modid = CnpcPlus.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class CnpcPlusClient {

    public CnpcPlusClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SmeltingMenus.TYPE, SmeltingScreen::new);
    }
}
