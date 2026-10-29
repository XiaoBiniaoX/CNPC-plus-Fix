package bin.cnpcplus;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import bin.cnpcplus.smelting.SmeltingMenus;
import bin.cnpcplus.smelting.client.SmeltingScreen;
import top.cnpcplus.anvil.AnvilMenus;
import top.cnpcplus.anvil.client.GuiNpcAnvilRecipes;
import top.cnpcplus.smithing.SmithingMenus;
import top.cnpcplus.smithing.client.GuiNpcSmithingRecipes;
import top.cnpcplus.drop.DropMenus;
import top.cnpcplus.drop.client.GuiDropRecipes;

@Mod(value = CnpcPlus.MODID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
@EventBusSubscriber(modid = CnpcPlus.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class CnpcPlusClient {

    public CnpcPlusClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SmeltingMenus.TYPE, SmeltingScreen::new);
        event.register(AnvilMenus.TYPE, GuiNpcAnvilRecipes::new);
        event.register(SmithingMenus.TYPE, GuiNpcSmithingRecipes::new);
        event.register(DropMenus.TYPE, GuiDropRecipes::new);
    }
}
