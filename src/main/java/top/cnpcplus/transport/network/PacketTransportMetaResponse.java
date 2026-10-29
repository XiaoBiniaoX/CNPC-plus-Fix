package top.cnpcplus.transport.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端→客户端：传送点扩展元数据（Entries=名字→meta，Current=当前位置名，CurrentMeta）。 */
public class PacketTransportMetaResponse implements CustomPacketPayload {
    public static final Type<PacketTransportMetaResponse> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "transport_meta_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTransportMetaResponse> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.encode(buf), PacketTransportMetaResponse::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    private final CompoundTag data;
    private final boolean editor;

    public PacketTransportMetaResponse(CompoundTag data, boolean editor) {
        this.data = data == null ? new CompoundTag() : data;
        this.editor = editor;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
        buf.writeBoolean(this.editor);
    }

    public static PacketTransportMetaResponse decode(FriendlyByteBuf buf) {
        return new PacketTransportMetaResponse(buf.readNbt(), buf.readBoolean());
    }

    public void handle(IPayloadContext context) {
        if (context.flow().isClientbound()) context.enqueueWork(() ->
                top.cnpcplus.transport.client.TransportClientCache.acceptMeta(this.data, this.editor));
    }
}
