package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.client.TransportClientCache;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 241z-3：服务端→客户端图片**分块**回传。
 *
 * <p>为什么分块：上传早就分块了（241v），但回传仍是整包；PNG 一大就超过网络包上限被静默丢弃
 * → 编辑器能看图（本地预览）而玩家面板永远没图。这里与上传同款：32KB/块 + last 标记。
 */
public class PacketTransportImageResponse {
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
        if (len < 0 || len > CHUNK) {
            byte[] skip = new byte[buf.readableBytes()];
            buf.readBytes(skip);
            return new PacketTransportImageResponse(key, seq, false, new byte[0]);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PacketTransportImageResponse(key, seq, last, data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> receive(this.key, this.seq, this.last, this.data)));
        ctx.get().setPacketHandled(true);
    }

    private static void receive(String key, int seq, boolean last, byte[] data) {
        String safe = TransportKeys.sanitize(key);
        if (safe.isEmpty()) return;
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
        if (all.length > 0) TransportClientCache.acceptImage(safe, all);
    }
}
