package top.cnpcplus.transport.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.CustomNpcsPermissions;
import top.cnpcplus.transport.TransportImageIO;
import top.cnpcplus.transport.TransportKeys;

/**
 * 客户端→服务端：上传传送点图片。权限同 NPC_ADVANCED（编辑传送点即需此权限）。
 * 使用统一NeoForge payload通道，保留旧整图上传接口。
 */
public class PacketTransportImageUpload implements CustomPacketPayload {
    public static final Type<PacketTransportImageUpload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "transport_image_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTransportImageUpload> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.encode(buf), PacketTransportImageUpload::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private final String key;
    private final byte[] data;

    public PacketTransportImageUpload(String key, byte[] data) {
        this.key = key;
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.key, 64);
        buf.writeVarInt(this.data.length);
        buf.writeBytes(this.data);
    }

    public static PacketTransportImageUpload decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(64);
        int len = buf.readVarInt();
        if (len < 0 || len > TransportKeys.MAX_IMAGE_BYTES || len > buf.readableBytes())
            throw new IllegalArgumentException("Invalid transport image length: " + len);
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new PacketTransportImageUpload(key, data);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)
                    || !CustomNpcsPermissions.hasPermission(sender, CustomNpcsPermissions.NPC_ADVANCED)) return;
            String safe = TransportKeys.sanitize(this.key);
            if (safe.isEmpty() || this.data.length == 0) return;
            boolean ok = TransportImageIO.write(safe, this.data);
            try {
                net.minecraft.nbt.CompoundTag out = new net.minecraft.nbt.CompoundTag();
                out.putBoolean("ImageSaved", ok);
                out.putString("ImageKey", safe);
                PacketDistributor.sendToPlayer(sender, new PacketTransportMetaResponse(out, true));
            } catch (Throwable ignored) {
            }
        });
    }
}
