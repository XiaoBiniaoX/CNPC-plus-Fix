package bin.cnpcplus;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.recipe.RecipeDebugCommand;
import bin.cnpcplus.recipe.RecipeGlobalBootstrap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = CnpcPlus.MODID, name = CnpcPlus.NAME, version = CnpcPlus.VERSION,
        dependencies = "required-after:mixinbooter;required-after:customnpcs")
public class CnpcPlus {
    public static final String MODID = "cnpcplus";
    public static final String NAME = "CNPCPlus";
    public static final String VERSION = "3.0.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        CnpcPlusConfig.init(event.getSuggestedConfigurationFile());
        CraftingViewNetwork.init();
        LOGGER.info("CNPCPlus patch loaded - by Bin");
    }

    @Mod.EventHandler
    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(RecipeGlobalBootstrap.class);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new RecipeDebugCommand());
        RecipeGlobalBootstrap.onServerStarting(event.getServer());
    }
}
