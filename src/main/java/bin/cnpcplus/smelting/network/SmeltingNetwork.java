package bin.cnpcplus.smelting.network;

import bin.cnpcplus.CnpcPlus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SmeltingNetwork {
    private SmeltingNetwork() {}
    public static void register(IEventBus bus) { bus.addListener(SmeltingNetwork::registerPayloads); }
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(CnpcPlus.MODID).versioned("1").playToServer(PacketSmeltingAction.TYPE, PacketSmeltingAction.STREAM_CODEC, PacketSmeltingAction::handle);
        event.registrar(CnpcPlus.MODID).versioned("1").playToClient(PacketSmeltingSync.TYPE, PacketSmeltingSync.STREAM_CODEC, PacketSmeltingSync::handle);
    }
}
