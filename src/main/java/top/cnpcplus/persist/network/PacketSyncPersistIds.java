package top.cnpcplus.persist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Full list of persisted recipe ids → client. */
public class PacketSyncPersistIds {

    private final List<ResourceLocation> ids;

    public PacketSyncPersistIds(List<ResourceLocation> ids) {
        this.ids = ids;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.ids.size());
        for (ResourceLocation id : this.ids) {
            buf.writeResourceLocation(id);
        }
    }

    public static PacketSyncPersistIds decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > 4096) throw new IllegalArgumentException("Invalid persisted recipe count: " + n);
        List<ResourceLocation> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(buf.readResourceLocation());
        }
        return new PacketSyncPersistIds(ids);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> top.cnpcplus.persist.client.PersistPacketClientHandler.setAll(this.ids)));
        ctx.get().setPacketHandled(true);
    }
}
