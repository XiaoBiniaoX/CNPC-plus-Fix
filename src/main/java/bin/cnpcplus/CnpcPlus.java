package bin.cnpcplus;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.recipe.RecipeGlobalBootstrap;
import bin.cnpcplus.quest.QuestCompletionEvents;
import bin.cnpcplus.smelting.SmeltingBurnTimeHandler;
import bin.cnpcplus.smelting.SmeltingGuiOpener;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = CnpcPlus.MODID, name = CnpcPlus.NAME, version = CnpcPlus.VERSION,
        dependencies = "required-after:mixinbooter;required-after:customnpcs",
        guiFactory = "bin.cnpcplus.config.CnpcPlusGuiFactory")
public class CnpcPlus {
    public static final String MODID = "cnpcplus";
    public static final String NAME = "CNPCPlus";
    public static final String VERSION = "3.2.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        CnpcPlusConfig.init(event.getSuggestedConfigurationFile());
        CraftingViewNetwork.init();
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(RecipeGlobalBootstrap.class);
        MinecraftForge.EVENT_BUS.register(new QuestCompletionEvents());
        MinecraftForge.EVENT_BUS.register(new SmeltingBurnTimeHandler());
        SmeltingGuiOpener.register();
    }

    /** Smelting recipes are per save, so the cache must not survive a world change. */
    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        SmeltingRecipeRegistry.clearCache();
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        RecipeGlobalBootstrap.onServerStarting(event.getServer());
        // Only now is CustomNpcs.Dir usable and vanilla recipe registration done.
        SmeltingRecipeRegistry.markReady();
    }
}
