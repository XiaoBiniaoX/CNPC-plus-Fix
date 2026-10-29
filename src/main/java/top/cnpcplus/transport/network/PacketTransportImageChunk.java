package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import noppes.npcs.CustomNpcsPermissions;
import top.cnpcplus.transport.TransportImageIO;
import top.cnpcplus.transport.TransportKeys;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 241v：传送点图片**分块上传**。
 *
 * <p>为什么分块：整包上传 1.27MB 的 PNG **超过网络包上限 → 静默丢包** →
 * 服务端永远收不到图片文件（表现为"玩家页没图、重开编辑器图没了"，而编辑器里能看到，
 * 因为那是客户端本地预览）。日志证据：客户端有 `image import`，服务端**没有** `image upload`。
 *
 * <p>协议：key + seq + data(≤32KB)；客户端顺序发 0..n-1，最后再发一个 seq=n 的空块表示"收齐了"。
 */
@EventBusSubscriber(modid = "cnpcplus")
public class PacketTransportImageChunk implements CustomPacketPayload {
    public static final Type<PacketTransportImageChunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "transport_image_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTransportImageChunk> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.encode(buf), PacketTransportImageChunk::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
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
        if (seq < 0 || seq > TransportKeys.MAX_IMAGE_BYTES / CHUNK
                || len < 0 || len > CHUNK || len > buf.readableBytes())
            throw new IllegalArgumentException("Invalid transport image chunk");
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PacketTransportImageChunk(key, seq, data);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)
                    || !CustomNpcsPermissions.hasPermission(sender, CustomNpcsPermissions.NPC_ADVANCED)) return;
            String safe = TransportKeys.sanitize(this.key);
            if (safe.isEmpty()) return;
            String transfer = sender.getUUID() + ":" + safe;
            // 主线程串行；同名图片的不同玩家不可拼到一起，未完成上传总数有界。
            if (!BUFFERS.containsKey(transfer) && BUFFERS.size() >= 32) return;
            ByteArrayOutputStream buf = BUFFERS.computeIfAbsent(transfer, k -> new ByteArrayOutputStream());
            int next = NEXT_SEQ.getOrDefault(transfer, 0);
            if (this.seq == next) {
                if (this.data.length > 0) {
                    buf.write(this.data, 0, this.data.length);
                }
                NEXT_SEQ.put(transfer, next + 1);
            } else {
                // 乱序/丢块：丢弃这次传输，等客户端重传
                BUFFERS.remove(transfer);
                NEXT_SEQ.remove(transfer);
                return;
            }
            if (this.data.length > 0) {
                // 数据块：等收齐（下一个 seq 的空块才是"结束"标记）
                if (buf.size() > TransportKeys.MAX_IMAGE_BYTES) {
                    BUFFERS.remove(transfer);
                    NEXT_SEQ.remove(transfer);
                }
                return;
            }
            // 空块 = 结束 → 写盘
            byte[] all = buf.toByteArray();
            BUFFERS.remove(transfer);
            NEXT_SEQ.remove(transfer);
            boolean ok = all.length > 0 && TransportImageIO.write(safe, all);
            try {
                net.minecraft.nbt.CompoundTag out = new net.minecraft.nbt.CompoundTag();
                out.putBoolean("ImageSaved", ok);
                out.putString("ImageKey", safe);
                PacketDistributor.sendToPlayer(sender, new PacketTransportMetaResponse(out, true));
            } catch (Throwable ignored) {
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        String prefix = event.getEntity().getUUID() + ":";
        BUFFERS.keySet().removeIf(key -> key.startsWith(prefix));
        NEXT_SEQ.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        BUFFERS.clear();
        NEXT_SEQ.clear();
    }
}
