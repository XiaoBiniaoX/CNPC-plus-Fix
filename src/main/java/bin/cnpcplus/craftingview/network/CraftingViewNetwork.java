package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.network.PacketRecipePersist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CraftingViewNetwork {
    private CraftingViewNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CraftingViewNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CnpcPlus.MODID).versioned("1");
        registrar.playToServer(
                PacketFillCraftingGrid.TYPE,
                PacketFillCraftingGrid.STREAM_CODEC,
                PacketFillCraftingGrid::handle
        );
        registrar.playToServer(
                PacketRecipePersist.TYPE,
                PacketRecipePersist.STREAM_CODEC,
                PacketRecipePersist::handle
        );
    }
}