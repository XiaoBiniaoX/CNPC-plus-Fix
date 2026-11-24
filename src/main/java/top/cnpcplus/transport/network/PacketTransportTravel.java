package top.cnpcplus.transport.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.EventHooks;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.api.event.RoleEvent;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketDimensionTeleport;
import noppes.npcs.roles.RoleTransporter;
import top.cnpcplus.transport.TransportFare;

public record PacketTransportTravel(int npcId, int targetId, CompoundTag agreedFare) implements CustomPacketPayload {
    public static final Type<PacketTransportTravel> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "transport_travel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketTransportTravel> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.encode(buf), PacketTransportTravel::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(npcId);
        buf.writeVarInt(targetId);
        buf.writeNbt(agreedFare);
    }

    public static PacketTransportTravel decode(FriendlyByteBuf buf) {
        return new PacketTransportTravel(buf.readVarInt(), buf.readVarInt(), buf.readNbt());
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var npc = NoppesUtilServer.getEditingNpc(player);
            if (npc == null || npc.getId() != npcId || !(npc.role instanceof RoleTransporter role)) return;
            var source = role.getLocation();
            if (source != null && source.category != null) {
                travel(player, npc, source, source.category.locations.get(targetId), agreedFare);
            }
        });
    }

    private static void travel(ServerPlayer player, EntityNPCInterface npc, TransportLocation source,
                               TransportLocation target, CompoundTag agreedFare) {
        if (!npc.isAlive() || npc.level() != player.level() || npc.distanceToSqr(player) > 64
                || target == null || target.id == source.id
                || (!target.isDefault() && !PlayerData.get(player).transportData.transports.contains(target.id))) return;
        var fare = TransportFare.of(source);
        if (!fare.getBoolean("Enabled") || !fare.equals(agreedFare)) {
            player.sendSystemMessage(Component.translatable("cnpcplus.transporter.fare.changed"));
            return;
        }
        var costs = TransportFare.items(fare, player.registryAccess());
        if (!TransportFare.pay(player.getInventory(), costs, false)) {
            player.sendSystemMessage(Component.translatable("cnpcplus.transporter.fare.missing"));
            return;
        }
        if (player.getServer().getLevel(target.dimension) == null) return;
        var use = new RoleEvent.TransporterUseEvent(player, npc.wrappedNPC, target);
        if (EventHooks.onNPCRole(npc, use)) return;
        if (!TransportFare.pay(player.getInventory(), costs, true)) {
            player.sendSystemMessage(Component.translatable("cnpcplus.transporter.fare.missing"));
            return;
        }
        SPacketDimensionTeleport.teleportPlayer(player, target.pos.getX(), target.pos.getY(), target.pos.getZ(), target.dimension);
    }
}
