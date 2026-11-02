package top.cnpcplus.bossbar;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Color authority is server-only; progress/name/visibility remain vanilla packets. */
public record BossBarPacket(UUID id, int rgb, boolean removed) implements CustomPacketPayload {
    public static final Type<BossBarPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "bossbar"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossBarPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> { buf.writeUUID(packet.id); buf.writeInt(packet.rgb); buf.writeBoolean(packet.removed); },
            buf -> new BossBarPacket(buf.readUUID(), buf.readInt() & 0xFFFFFF, buf.readBoolean()));

    @Override public Type<BossBarPacket> type() { return TYPE; }

    public static void send(ServerPlayer player, UUID id, int rgb, boolean removed) {
        if (NetworkRegistry.hasChannel(player.connection, TYPE.id())) PacketDistributor.sendToPlayer(player, new BossBarPacket(id, rgb, removed));
    }

    public static void handle(BossBarPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> top.cnpcplus.bossbar.client.BossBarColors.receive(packet));
    }
}
