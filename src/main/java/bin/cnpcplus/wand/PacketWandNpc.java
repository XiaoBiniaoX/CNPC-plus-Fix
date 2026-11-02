package bin.cnpcplus.wand;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.data.NpcExternalData;
import bin.cnpcplus.clone.CloneMounts;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcs;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobSpawner;
import bin.cnpcplus.spawner.SpawnerData;

/** Current-page preview/copy requests do not change the server's editing NPC. */
public final class PacketWandNpc implements IMessage {
    public int id, action, tab;
    public long request;
    public String name = "";
    public NBTTagCompound snapshot;
    public PacketWandNpc() { }
    public PacketWandNpc(int id, int action, long request, int tab, String name) { this.id = id; this.action = action; this.request = request; this.tab = tab; this.name = name; }
    @Override public void fromBytes(ByteBuf buf) { id = buf.readInt(); action = buf.readInt(); request = buf.readLong(); tab = buf.readInt(); name = ByteBufUtils.readUTF8String(buf); snapshot = ByteBufUtils.readTag(buf); }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(id); buf.writeInt(action); buf.writeLong(request); buf.writeInt(tab); ByteBufUtils.writeUTF8String(buf, name); ByteBufUtils.writeTag(buf, snapshot); }
    public static final class Handler implements IMessageHandler<PacketWandNpc, IMessage> {
        @Override public IMessage onMessage(PacketWandNpc packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Object tool = player.getHeldItemMainhand().getItem();
                if (CustomNpcs.OpsOnly && !NoppesUtilServer.isOp(player) || !(tool == CustomItems.wand || tool == CustomItems.cloner || tool == CustomItems.scripter)
                        || packet.action < 0 || packet.action > 3 || !CustomNpcsPermissions.hasPermission(player,
                        packet.action == 1 || packet.action == 2 ? CustomNpcsPermissions.NPC_CLONE
                                : packet.action == 3 ? CustomNpcsPermissions.NPC_ADVANCED : CustomNpcsPermissions.NPC_GUI)) return;
                if (packet.action == 1 && tool != CustomItems.cloner) return;
                NBTTagCompound snapshot = null;
                if (packet.action == 3) {
                    // Assigned spawner entry: id is the edited NPC, tab carries its stable array slot.
                    EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
                    if (npc == null || npc.getEntityId() != packet.id || !(npc.jobInterface instanceof JobSpawner)) return;
                    SpawnerData.Entry entry = SpawnerData.get((JobSpawner) npc.jobInterface).get(packet.tab);
                    if (entry != null) snapshot = entry.resolve();
                } else if (packet.action == 2) {
                    if (packet.tab < 1 || packet.tab > 9 || packet.name.length() > 256) return;
                    snapshot = ServerCloneController.Instance.getCloneData(player, packet.name, packet.tab);
                    if (snapshot != null) snapshot = snapshot.copy();
                } else {
                    Entity entity = player.world.getEntityByID(packet.id);
                    if (entity instanceof EntityNPCInterface && !entity.isDead) {
                        try {
                            if (packet.action == 1) snapshot = CloneMounts.capture(entity);
                            else { snapshot = new NBTTagCompound(); entity.writeToNBTAtomically(snapshot); }
                        } catch (java.io.IOException | RuntimeException error) {
                            player.sendMessage(new net.minecraft.util.text.TextComponentString("未复制：" + error.getMessage()));
                        }
                    }
                }
                if (snapshot != null && !NpcExternalData.makePortable(snapshot)) snapshot = null;
                if (snapshot != null && packet.action == 1) {
                    ServerCloneController.Instance.cleanTags(snapshot); PlayerData.get(player).cloned = snapshot.copy();
                }
                packet.snapshot = snapshot; CraftingViewNetwork.CHANNEL.sendTo(packet, player);
            });
            return null;
        }
    }
    public static final class ClientHandler implements IMessageHandler<PacketWandNpc, IMessage> {
        @SideOnly(Side.CLIENT) @Override public IMessage onMessage(PacketWandNpc packet, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> bin.cnpcplus.wand.client.PreviewReceiver.receive(packet)); return null;
        }
    }
}
