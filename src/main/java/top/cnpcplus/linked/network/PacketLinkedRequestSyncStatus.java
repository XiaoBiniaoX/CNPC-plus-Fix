package top.cnpcplus.linked.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.controllers.LinkedNpcController;
import top.cnpcplus.linked.LinkedSyncFlags;

import java.util.HashMap;
import java.util.function.Supplier;

public class PacketLinkedRequestSyncStatus {

    public void encode(FriendlyByteBuf buf) {
    }

    public static PacketLinkedRequestSyncStatus decode(FriendlyByteBuf buf) {
        return new PacketLinkedRequestSyncStatus();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null) return;
            if (LinkedNpcController.Instance == null) return;
            var map = new HashMap<String, Boolean>();
            for (var data : LinkedNpcController.Instance.list) {
                map.put(data.name, LinkedSyncFlags.isSyncScripts(data.name));
            }
            LinkedPacketHandler.CHANNEL.sendTo(new PacketLinkedSyncStatus(map),
                    sender.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        });
        ctx.get().setPacketHandled(true);
    }
}