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
import top.cnpcplus.anvil.AnvilCraftHandler;
import top.cnpcplus.anvil.AnvilMenus;
import top.cnpcplus.anvil.AnvilRecipeRegistry;
import top.cnpcplus.smithing.SmithingMenus;
import top.cnpcplus.smithing.SmithingSerializers;
import top.cnpcplus.smithing.SmithingRecipeBootstrap;
import top.cnpcplus.drop.DropMenus;
import top.cnpcplus.drop.DropRecipeRegistry;
import top.cnpcplus.drop.DropCraftHandler;
import top.cnpcplus.drop.fx.runtime.DropFxEngine;
import top.cnpcplus.drop.fx.runtime.ProjectileFxTracker;
import org.slf4j.Logger;

@Mod("cnpcplus")
public class CnpcPlus {
    public static final String MODID = "cnpcplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcPlus(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CnpcPlusConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, top.cnpcplus.config.NpcAiOptimizerConfig.SPEC, "cnpcplus-ai.toml");
        CraftingViewNetwork.register(modEventBus);
        modEventBus.addListener(SmeltingMenus::register);
        modEventBus.addListener(AnvilMenus::register);
        modEventBus.addListener(SmithingMenus::register);
        modEventBus.addListener(DropMenus::register);
        SmithingSerializers.register(modEventBus);
        NeoForge.EVENT_BUS.register(RecipeGlobalBootstrap.class);
        NeoForge.EVENT_BUS.register(SmeltingRecipeBootstrap.class);
        NeoForge.EVENT_BUS.register(AnvilCraftHandler.class);
        NeoForge.EVENT_BUS.register(AnvilRecipeRegistry.class);
        NeoForge.EVENT_BUS.register(SmithingRecipeBootstrap.class);
        NeoForge.EVENT_BUS.register(DropRecipeRegistry.class);
        NeoForge.EVENT_BUS.register(DropCraftHandler.class);
        NeoForge.EVENT_BUS.register(DropFxEngine.class);
        NeoForge.EVENT_BUS.register(ProjectileFxTracker.class);
        NeoForge.EVENT_BUS.register(top.cnpcplus.villagertrade.TradeDataStore.class);
        NeoForge.EVENT_BUS.register(ScoreboardFixListener.class);
        NeoForge.EVENT_BUS.register(NoppesCommandBlockAccess.class);
        NeoForge.EVENT_BUS.register(bin.cnpcplus.playerdata.PlayerDataLifecycle.class);
        NeoForge.EVENT_BUS.register(bin.cnpcplus.playerdata.PlayerAutoSave.class);
        NeoForge.EVENT_BUS.register(bin.cnpcplus.playerdata.PlayerRespawnFix.class);
        NeoForge.EVENT_BUS.register(bin.cnpcplus.command.CmdPlayerData.class);
        NeoForge.EVENT_BUS.register(bin.cnpcplus.leakfix.MarkDataCleanup.class);
        NeoForge.EVENT_BUS.register(bin.cnpcplus.playerdata.PlayerTrackingCleanup.class);
    }
}
