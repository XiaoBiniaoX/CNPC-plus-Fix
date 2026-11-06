package top.cnpcplus.shader;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShaderPacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ShaderPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "shaders"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShaderPacket> STREAM_CODEC = StreamCodec.of((buf, packet) -> packet.encode(buf), ShaderPacket::decode);
    @Override public Type<ShaderPacket> type() { return TYPE; }
    public static void send(ServerPlayer player, CompoundTag data) {
        if (data.sizeInBytes() > 16384) throw new IllegalArgumentException("Shader packet exceeds 16 KiB");
        if (player.connection.hasChannel(TYPE)) PacketDistributor.sendToPlayer(player, new ShaderPacket(data));
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeNbt(data); }
    public static ShaderPacket decode(FriendlyByteBuf buffer) {
        var tag = buffer.readNbt(net.minecraft.nbt.NbtAccounter.create(16384));
        if (!(tag instanceof CompoundTag data)) throw new IllegalArgumentException("Shader payload must be a compound tag");
        return new ShaderPacket(data);
    }
    public static void handle(ShaderPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> top.cnpcplus.shader.client.ClientShaders.accept(packet.data));
    }
}
