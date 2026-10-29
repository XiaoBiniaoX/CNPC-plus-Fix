package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import top.cnpcplus.transport.TransportImageIO;
import top.cnpcplus.transport.TransportKeys;

/** 客户端→服务端：按文件键请求传送点图片字节。 */
public class PacketTransportImageRequest implements CustomPacketPayload {
    public static final Type<PacketTransportImageRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "transport_image_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTransportImageRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.encode(buf), PacketTransportImageRequest::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private final String key;

    public PacketTransportImageRequest(String key) { this.key = key; }

    public void encode(FriendlyByteBuf buf) { buf.writeUtf(this.key, 64); }

    public static PacketTransportImageRequest decode(FriendlyByteBuf buf) {
        return new PacketTransportImageRequest(buf.readUtf(64));
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            String safe = TransportKeys.sanitize(this.key);
            if (safe.isEmpty()) return;
            byte[] data = TransportImageIO.read(safe);
            if (data != null) {
                // 241z-3：分块回传（整包超上限会被丢包 → 玩家页永远没图）
                int chunk = PacketTransportImageResponse.chunkSize();
                int seq = 0;
                for (int off = 0; off < data.length; off += chunk, seq++) {
                    int len = Math.min(chunk, data.length - off);
                    byte[] part = java.util.Arrays.copyOfRange(data, off, off + len);
                    PacketDistributor.sendToPlayer(sender, new PacketTransportImageResponse(safe, seq, off + len >= data.length, part));
                }
            }
        });
    }
}
