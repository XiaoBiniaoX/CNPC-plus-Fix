package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.transport.TransportImageIO;
import top.cnpcplus.transport.TransportKeys;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 241v：传送点图片**分块上传**。
 *
 * <p>为什么分块：整包上传 1.27MB 的 PNG **超过网络包上限 → 静默丢包** →
 * 服务端永远收不到图片文件（表现为"玩家页没图、重开编辑器图没了"，而编辑器里能看到，
 * 因为那是客户端本地预览）。日志证据：客户端有 `image import`，服务端**没有** `image upload`。
 *
 * <p>协议：key + seq + data(≤32KB)；客户端顺序发 0..n-1，最后再发一个 seq=n 的空块表示"收齐了"。
 */
public class PacketTransportImageChunk {
    private static final int CHUNK = 32 * 1024;
    private static final Map<String, ByteArrayOutputStream> BUFFERS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> NEXT_SEQ = new ConcurrentHashMap<>();

    private final String key;
    private final int seq;
    private final byte[] data;

    public PacketTransportImageChunk(String key, int seq, byte[] data) {
        this.key = key;
        this.seq = seq;
        this.data = data;
    }

    public static int chunkSize() { return CHUNK; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.key, 64);
        buf.writeVarInt(this.seq);
        buf.writeVarInt(this.data.length);
        buf.writeBytes(this.data);
    }

    public static PacketTransportImageChunk decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(64);
        int seq = buf.readVarInt();
        int len = buf.readVarInt();
        if (len < 0 || len > CHUNK) {
            byte[] skip = new byte[buf.readableBytes()];
            buf.readBytes(skip);
            return new PacketTransportImageChunk(key, seq, new byte[0]);
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PacketTransportImageChunk(key, seq, data);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            String safe = TransportKeys.sanitize(this.key);
            if (safe.isEmpty()) return;
            ByteArrayOutputStream buf = BUFFERS.computeIfAbsent(safe, k -> new ByteArrayOutputStream());
            int next = NEXT_SEQ.getOrDefault(safe, 0);
            if (this.seq == next) {
                if (this.data.length > 0) {
                    buf.write(this.data, 0, this.data.length);
                }
                NEXT_SEQ.put(safe, next + 1);
            } else {
                // 乱序/丢块：丢弃这次传输，等客户端重传
                BUFFERS.remove(safe);
                NEXT_SEQ.remove(safe);
                return;
            }
            if (this.data.length > 0) {
                // 数据块：等收齐（下一个 seq 的空块才是"结束"标记）
                if (buf.size() > TransportKeys.MAX_IMAGE_BYTES) {
                    BUFFERS.remove(safe);
                    NEXT_SEQ.remove(safe);
                }
                return;
            }
            // 空块 = 结束 → 写盘
            byte[] all = buf.toByteArray();
            BUFFERS.remove(safe);
            NEXT_SEQ.remove(safe);
            boolean ok = all.length > 0 && TransportImageIO.write(safe, all);
            try {
                net.minecraft.nbt.CompoundTag out = new net.minecraft.nbt.CompoundTag();
                out.putBoolean("ImageSaved", ok);
                out.putString("ImageKey", safe);
                TransportPacketHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                        new PacketTransportMetaResponse(out, true));
            } catch (Throwable ignored) {
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
