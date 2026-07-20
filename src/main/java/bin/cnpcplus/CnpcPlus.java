package bin.cnpcplus;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.recipe.RecipeDebugCommand;
import bin.cnpcplus.recipe.RecipeGlobalBootstrap;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod("cnpcplus")
public class CnpcPlus {
    public static final String MODID = "cnpcplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcPlus(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CnpcPlusConfig.SPEC);
        CraftingViewNetwork.register(modEventBus);
        NeoForge.EVENT_BUS.register(RecipeGlobalBootstrap.class);
        NeoForge.EVENT_BUS.register(RecipeDebugCommand.class);
        NeoForge.EVENT_BUS.register(ScoreboardFixListener.class);
        LOGGER.info("CNPCPlus patch loaded - by Bin");
    }
}