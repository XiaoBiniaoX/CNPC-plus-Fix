package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.transport.TransportImageIO;
import top.cnpcplus.transport.TransportKeys;

import java.util.function.Supplier;

/** 客户端→服务端：按文件键请求传送点图片字节。 */
public class PacketTransportImageRequest {
    private final String key;

    public PacketTransportImageRequest(String key) { this.key = key; }

    public void encode(FriendlyByteBuf buf) { buf.writeUtf(this.key, 64); }

    public static PacketTransportImageRequest decode(FriendlyByteBuf buf) {
        return new PacketTransportImageRequest(buf.readUtf(64));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null) return;
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
                    TransportPacketHandler.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                            new PacketTransportImageResponse(safe, seq, off + len >= data.length, part));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
