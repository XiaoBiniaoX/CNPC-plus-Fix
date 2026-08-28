package top.cnpcplus.persist.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Single recipe persist flag update → client. */
public class PacketPersistStatus {

    private final ResourceLocation id;
    private final boolean persisted;

    public PacketPersistStatus(ResourceLocation id, boolean persisted) {
        this.id = id;
        this.persisted = persisted;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id);
        buf.writeBoolean(this.persisted);
    }

    public static PacketPersistStatus decode(FriendlyByteBuf buf) {
        return new PacketPersistStatus(buf.readResourceLocation(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> top.cnpcplus.persist.client.PersistPacketClientHandler.setStatus(this.id, this.persisted)));
        ctx.get().setPacketHandled(true);
    }
}
