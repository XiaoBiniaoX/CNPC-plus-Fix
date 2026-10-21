package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.client.TransportClientCache;

import java.util.function.Supplier;

/** 服务端→客户端：传送点图片字节回传。 */
public class PacketTransportImageResponse {
    private final String key;
    private final byte[] data;

    public PacketTransportImageResponse(String key, byte[] data) {
        this.key = key;
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.key, 64);
        buf.writeVarInt(this.data.length);
        buf.writeBytes(this.data);
    }

    public static PacketTransportImageResponse decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(64);
        int len = buf.readVarInt();
        if (len < 0 || len > TransportKeys.MAX_IMAGE_BYTES) {
            byte[] skip = new byte[buf.readableBytes()];
            buf.readBytes(skip);
            return new PacketTransportImageResponse(key, new byte[0]);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PacketTransportImageResponse(key, data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> TransportClientCache.acceptImage(this.key, this.data)));
        ctx.get().setPacketHandled(true);
    }
}
