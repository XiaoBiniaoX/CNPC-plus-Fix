package bin.cnpcplus.wand;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.entity.EntityNPCInterface;

/** A route snapshot is sent only after a successful path-tool edit. */
public final class PacketWandPathSync implements IMessage {
    private int entityId;
    private int[][] route = new int[0][];

    public PacketWandPathSync() {}

    private PacketWandPathSync(EntityNPCInterface npc) {
        entityId = npc.getEntityId();
        List<int[]> path = npc.ais.getMovingPath();
        route = new int[path.size()][3];
        for (int i = 0; i < path.size(); i++) System.arraycopy(path.get(i), 0, route[i], 0, 3);
    }

    public static void send(EntityNPCInterface npc) {
        if (npc.world.isRemote) return;
        PacketWandPathSync packet = new PacketWandPathSync(npc);
        CraftingViewNetwork.CHANNEL.sendToAllAround(packet, new NetworkRegistry.TargetPoint(
                npc.dimension, npc.posX, npc.posY, npc.posZ, 48));
    }

    @Override public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        int size = buf.readInt();
        if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid moving path length: " + size);
        route = new int[size][3];
        for (int[] point : route) { point[0] = buf.readInt(); point[1] = buf.readInt(); point[2] = buf.readInt(); }
    }

    @Override public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId); buf.writeInt(route.length);
        for (int[] point : route) { buf.writeInt(point[0]); buf.writeInt(point[1]); buf.writeInt(point[2]); }
    }

    public static final class ClientHandler implements IMessageHandler<PacketWandPathSync, IMessage> {
        @SideOnly(Side.CLIENT) @Override public IMessage onMessage(PacketWandPathSync packet, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                Entity entity = net.minecraft.client.Minecraft.getMinecraft().world == null ? null
                        : net.minecraft.client.Minecraft.getMinecraft().world.getEntityByID(packet.entityId);
                if (entity instanceof EntityNPCInterface) {
                    List<int[]> copy = new ArrayList<>(packet.route.length);
                    for (int[] point : packet.route) copy.add(point.clone());
                    ((EntityNPCInterface) entity).ais.setMovingPath(copy);
                }
            });
            return null;
        }
    }
}
