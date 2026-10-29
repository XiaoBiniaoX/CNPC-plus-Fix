package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.transport.TransportKeys;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 241z-3：服务端→客户端图片**分块**回传。
 *
 * <p>为什么分块：上传早就分块了（241v），但回传仍是整包；PNG 一大就超过网络包上限被静默丢弃
 * → 编辑器能看图（本地预览）而玩家面板永远没图。这里与上传同款：32KB/块 + last 标记。
 */
public class PacketTransportImageResponse implements CustomPacketPayload {
    public static final Type<PacketTransportImageResponse> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "transport_image_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTransportImageResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.encode(buf), PacketTransportImageResponse::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private static final int CHUNK = 32 * 1024;
    private static final Map<String, ByteArrayOutputStream> BUFFERS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> NEXT_SEQ = new ConcurrentHashMap<>();

    private final String key;
    private final int seq;
    private final boolean last;
    private final byte[] data;

    public PacketTransportImageResponse(String key, int seq, boolean last, byte[] data) {
        this.key = key;
        this.seq = seq;
        this.last = last;
        this.data = data;
    }

    public static int chunkSize() { return CHUNK; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.key, 64);
        buf.writeVarInt(this.seq);
        buf.writeBoolean(this.last);
        buf.writeVarInt(this.data.length);
        buf.writeBytes(this.data);
    }

    public static PacketTransportImageResponse decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(64);
        int seq = buf.readVarInt();
        boolean last = buf.readBoolean();
        int len = buf.readVarInt();
        if (seq < 0 || seq >= TransportKeys.MAX_IMAGE_BYTES / CHUNK
                || len < 0 || len > CHUNK || len > buf.readableBytes())
            throw new IllegalArgumentException("Invalid transport image response");
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PacketTransportImageResponse(key, seq, last, data);
    }

    public void handle(IPayloadContext context) {
        if (context.flow().isClientbound()) context.enqueueWork(() -> receive(this.key, this.seq, this.last, this.data));
    }

    private static void receive(String key, int seq, boolean last, byte[] data) {
        String safe = TransportKeys.sanitize(key);
        if (safe.isEmpty()) return;
        if (!BUFFERS.containsKey(safe) && BUFFERS.size() >= 32) return;
        int next = NEXT_SEQ.getOrDefault(safe, 0);
        if (seq != next) {
            BUFFERS.remove(safe);
            NEXT_SEQ.remove(safe);
            return;
        }
        ByteArrayOutputStream buf = BUFFERS.computeIfAbsent(safe, k -> new ByteArrayOutputStream());
        if (data.length > 0) buf.write(data, 0, data.length);
        if (buf.size() > TransportKeys.MAX_IMAGE_BYTES) {
            BUFFERS.remove(safe);
            NEXT_SEQ.remove(safe);
            return;
        }
        NEXT_SEQ.put(safe, next + 1);
        if (!last) return;
        byte[] all = buf.toByteArray();
        BUFFERS.remove(safe);
        NEXT_SEQ.remove(safe);
        if (all.length > 0) top.cnpcplus.transport.client.TransportClientCache.acceptImage(safe, all);
    }

    public static void clear() { BUFFERS.clear(); NEXT_SEQ.clear(); }
}
