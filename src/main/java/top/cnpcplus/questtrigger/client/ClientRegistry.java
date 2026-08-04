package top.cnpcplus.questtrigger.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.CustomTabs;
import top.cnpcplus.CnpcPlus;
import top.cnpcplus.questtrigger.ModRegistry;

@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientRegistry {

    static {
        MinecraftForge.EVENT_BUS.addListener(QuestTriggerOverlay::onRenderLevelStage);
    }

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == CustomTabs.CNPCS.get()) {
            event.accept(() -> ModRegistry.QUEST_TRIGGER_ITEM.get());
        }
    }
}
