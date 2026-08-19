package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import bin.cnpcplus.recipe.network.PacketRecipePersist;
import bin.cnpcplus.trader.network.PacketTraderPage;
import bin.cnpcplus.trader.network.PacketTraderPageSync;
import bin.cnpcplus.follower.network.PacketFollowerDismiss;
import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import bin.cnpcplus.smelting.network.PacketSmeltingSync;
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
        registrar.playToServer(
                PacketTraderPage.TYPE,
                PacketTraderPage.STREAM_CODEC,
                PacketTraderPage::handle
        );
        registrar.playToServer(
                PacketNpcInvPage.TYPE,
                PacketNpcInvPage.STREAM_CODEC,
                PacketNpcInvPage::handle
        );
        registrar.playToServer(
                PacketFollowerDismiss.TYPE,
                PacketFollowerDismiss.STREAM_CODEC,
                PacketFollowerDismiss::handle
        );
        registrar.playToClient(
                PacketTraderPageSync.TYPE,
                PacketTraderPageSync.STREAM_CODEC,
                PacketTraderPageSync::handle
        );
        registrar.playToServer(
                PacketSmeltingAction.TYPE,
                PacketSmeltingAction.STREAM_CODEC,
                PacketSmeltingAction::handle
        );
        registrar.playToClient(
                PacketSmeltingSync.TYPE,
                PacketSmeltingSync.STREAM_CODEC,
                PacketSmeltingSync::handle
        );
    }
}
