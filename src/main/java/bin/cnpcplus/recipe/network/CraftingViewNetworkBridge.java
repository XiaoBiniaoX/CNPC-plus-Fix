package bin.cnpcplus.recipe.network;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import net.minecraft.entity.player.EntityPlayerMP;

/** Thin bridge so recipe.network does not circular-depend on craftingview package layout. */
public final class CraftingViewNetworkBridge {
    private CraftingViewNetworkBridge() {}

    public static void sendPersist(int syncId, boolean persist) {
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketRecipePersist(syncId, persist));
    }

    public static void sendPersistState(EntityPlayerMP player, int syncId, boolean persisted) {
        CraftingViewNetwork.CHANNEL.sendTo(new PacketPersistState(syncId, persisted), player);
    }

    public static void requestPersistState(int syncId) {
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketQueryPersist(syncId));
    }
}
