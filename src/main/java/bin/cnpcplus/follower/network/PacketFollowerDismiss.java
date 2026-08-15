package bin.cnpcplus.follower.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.containers.ContainerNPCFollower;
import noppes.npcs.roles.RoleFollower;

public class PacketFollowerDismiss implements IMessage {
    @Override public void fromBytes(ByteBuf buf) {}
    @Override public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketFollowerDismiss, IMessage> {
        @Override
        public IMessage onMessage(PacketFollowerDismiss message, MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (!(player.openContainer instanceof ContainerNPCFollower)) return;
                    RoleFollower role = ((ContainerNPCFollower) player.openContainer).role;
                    if (role == null || role.npc == null || role.npc.roleInterface != role
                            || role.npc.advanced.role != 2) return;
                    EntityPlayerMP owner = role.getOwner() instanceof EntityPlayerMP
                            ? (EntityPlayerMP) role.getOwner() : null;
                    if (owner == null || !owner.getUniqueID().equals(player.getUniqueID())) return;
                    role.killed();
                    role.owner = null;
                    role.npc.updateClient = true;
                    player.closeScreen();
                }
            });
            return null;
        }
    }
}
