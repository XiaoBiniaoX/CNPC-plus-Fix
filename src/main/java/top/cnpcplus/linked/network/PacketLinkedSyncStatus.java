package top.cnpcplus.linked.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketLinkedSyncStatus {
    private final Map<String, Boolean> statusMap;

    public PacketLinkedSyncStatus(Map<String, Boolean> statusMap) {
        this.statusMap = statusMap;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(statusMap.size());
        for (var entry : statusMap.entrySet()) {
            buf.writeUtf(entry.getKey(), 64);
            buf.writeBoolean(entry.getValue());
        }
    }

    public static PacketLinkedSyncStatus decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid linked status count: " + size);
        Map<String, Boolean> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(buf.readUtf(64), buf.readBoolean());
        }
        return new PacketLinkedSyncStatus(map);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LinkedSyncClientData.setStatusMap(statusMap);
        });
        ctx.get().setPacketHandled(true);
    }
}
