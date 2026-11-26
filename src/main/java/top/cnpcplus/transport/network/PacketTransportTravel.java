package top.cnpcplus.transport.network;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.EventHooks;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.api.event.RoleEvent;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketDimensionTeleport;
import noppes.npcs.roles.RoleTransporter;
import top.cnpcplus.transport.TransportFare;
import top.cnpcplus.transport.TransportLanding;

public record PacketTransportTravel(int npcId, int targetId, CompoundTag agreedFare) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(npcId);
        buf.writeVarInt(targetId);
        buf.writeNbt(agreedFare);
    }

    public static PacketTransportTravel decode(FriendlyByteBuf buf) {
        return new PacketTransportTravel(buf.readVarInt(), buf.readVarInt(), buf.readNbt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            var npc = NoppesUtilServer.getEditingNpc(player);
            if (npc == null || npc.getId() != npcId || !(npc.role instanceof RoleTransporter role)) return;
            var source = role.getLocation();
            if (source != null && source.category != null) {
                travel(player, npc, source, source.category.locations.get(targetId), agreedFare);
            }
        });
        context.setPacketHandled(true);
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
        var costs = TransportFare.items(fare);
        var destination = TransportLanding.find(player, target.dimension, target.pos);
        if (destination.isEmpty()) {
            player.sendSystemMessage(Component.translatable("cnpcplus.transporter.destination.unsafe"));
            return;
        }
        if (!TransportFare.pay(player.getInventory(), costs, false)) {
            player.sendSystemMessage(Component.translatable("cnpcplus.transporter.fare.missing"));
            return;
        }
        var use = new RoleEvent.TransporterUseEvent(player, npc.wrappedNPC, target);
        if (EventHooks.onNPCRole(npc, use)) return;
        if (!TransportFare.pay(player.getInventory(), costs, true)) {
            player.sendSystemMessage(Component.translatable("cnpcplus.transporter.fare.missing"));
            return;
        }
        var pos = destination.get();
        SPacketDimensionTeleport.teleportPlayer(player, pos.x, pos.y, pos.z, target.dimension);
    }
}
