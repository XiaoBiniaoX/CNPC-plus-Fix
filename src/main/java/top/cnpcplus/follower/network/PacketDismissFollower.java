package top.cnpcplus.follower.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.containers.ContainerNPCFollower;
import noppes.npcs.roles.RoleFollower;

import java.util.function.Supplier;

public final class PacketDismissFollower {
    public static void encode(PacketDismissFollower message, FriendlyByteBuf buffer) {}

    public static PacketDismissFollower decode(FriendlyByteBuf buffer) {
        return new PacketDismissFollower();
    }

    public static void handle(PacketDismissFollower message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null || !(player.containerMenu instanceof ContainerNPCFollower menu)) return;
            RoleFollower role = menu.role;
            if (role == null || role.npc == null || role.npc.role != role || role.getType() != 2) return;
            Player owner = role.getOwner();
            if (owner == null || !owner.getUUID().equals(player.getUUID())) return;
            role.killed();
            role.owner = null;
            role.npc.updateClient = true;
            player.closeContainer();
        });
        context.get().setPacketHandled(true);
    }
}
