package bin.cnpcplus.follower.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.containers.ContainerNPCFollower;
import noppes.npcs.roles.RoleFollower;

public record PacketFollowerDismiss() implements CustomPacketPayload {
    public static final Type<PacketFollowerDismiss> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "follower_dismiss"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFollowerDismiss> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new PacketFollowerDismiss());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketFollowerDismiss msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof ContainerNPCFollower menu)) return;
            RoleFollower role = menu.role;
            if (role == null || role.npc == null || role.npc.role != role || role.getType() != 2) return;
            Player owner = role.getOwner();
            if (owner == null || !owner.getUUID().equals(player.getUUID())) return;
            role.killed();
            role.owner = null;
            role.npc.updateClient = true;
            player.closeContainer();
        });
    }
}
