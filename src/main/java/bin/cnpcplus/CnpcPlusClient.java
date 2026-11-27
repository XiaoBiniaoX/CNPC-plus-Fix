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

    @net.neoforged.bus.api.SubscribeEvent
    public static void questTriggerTab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == noppes.npcs.CustomTabs.CNPCS.get())
            event.accept(top.cnpcplus.questtrigger.ModRegistry.QUEST_TRIGGER_ITEM.get());
    }

    public CnpcPlusClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(top.cnpcplus.shader.client.ClientShaders.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(top.cnpcplus.wand.client.WandHitboxOverlay::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(top.cnpcplus.wand.client.WandPathOverlay::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(top.cnpcplus.questtrigger.client.QuestTriggerOverlay::onRenderLevelStage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CnpcPlusClient::clearRecipeLists);
    }

    private static void clearRecipeLists(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        bin.cnpcplus.smelting.SmeltingClientData.set(java.util.List.of(), -1);
        top.cnpcplus.station.StationRecipes.CLIENT.clear();
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SmeltingMenus.TYPE, SmeltingScreen::new);
        event.register(AnvilMenus.TYPE, GuiNpcAnvilRecipes::new);
        event.register(SmithingMenus.TYPE, GuiNpcSmithingRecipes::new);
        event.register(DropMenus.TYPE, GuiDropRecipes::new);
        event.register(top.cnpcplus.station.StationMenu.TYPE, top.cnpcplus.station.StationScreen::new);
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void reloadShaders(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> top.cnpcplus.shader.client.ClientShaders.reloadAll());
    }
}
