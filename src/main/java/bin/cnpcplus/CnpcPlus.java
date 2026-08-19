package bin.cnpcplus;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.recipe.RecipeGlobalBootstrap;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
import bin.cnpcplus.smelting.SmeltingMenus;
import bin.cnpcplus.smelting.SmeltingRecipeBootstrap;
import org.slf4j.Logger;

@Mod("cnpcplus")
public class CnpcPlus {
    public static final String MODID = "cnpcplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcPlus(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CnpcPlusConfig.SPEC);
        CraftingViewNetwork.register(modEventBus);
        modEventBus.addListener(SmeltingMenus::register);
        NeoForge.EVENT_BUS.register(RecipeGlobalBootstrap.class);
        NeoForge.EVENT_BUS.register(SmeltingRecipeBootstrap.class);
        NeoForge.EVENT_BUS.register(ScoreboardFixListener.class);
        NeoForge.EVENT_BUS.register(NoppesCommandBlockAccess.class);
    }
}
