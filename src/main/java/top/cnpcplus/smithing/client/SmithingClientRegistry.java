package top.cnpcplus.smithing.client;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import top.cnpcplus.smithing.SmithingMenuAccess;

@EventBusSubscriber(modid = CnpcPlus.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class SmithingClientRegistry {

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SmithingClientData.set(java.util.List.of());
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof SmithingMenuAccess menu) {
            menu.cnpcplus$refreshRecipes();
        }
    }
}
