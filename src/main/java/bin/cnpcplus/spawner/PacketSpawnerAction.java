package bin.cnpcplus.spawner;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.Server;
import noppes.npcs.constants.EnumPacketClient;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobSpawner;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;

public final class PacketSpawnerAction implements IMessage {
    private int npcId, action, tab, slot, direction;
    private String name = "";
    public PacketSpawnerAction() { }
    public PacketSpawnerAction(int npcId, int action, int tab, int slot, int direction, String name) {
        this.npcId = npcId; this.action = action; this.tab = tab; this.slot = slot; this.direction = direction; this.name = name;
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(npcId); buf.writeInt(action); buf.writeInt(tab); buf.writeInt(slot); buf.writeInt(direction); ByteBufUtils.writeUTF8String(buf, name); }
    @Override public void fromBytes(ByteBuf buf) { npcId = buf.readInt(); action = buf.readInt(); tab = buf.readInt(); slot = buf.readInt(); direction = buf.readInt(); name = ByteBufUtils.readUTF8String(buf); }

    public static final class Handler implements IMessageHandler<PacketSpawnerAction, IMessage> {
        @Override public IMessage onMessage(PacketSpawnerAction message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
                if (npc == null || npc.getEntityId() != message.npcId || !(npc.jobInterface instanceof JobSpawner)
                        || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_ADVANCED)
                        || message.tab < 1 || message.tab > 9 || message.name.length() > 256) return;
                JobSpawner job = (JobSpawner) npc.jobInterface;
                NavigableMap<Integer, SpawnerData.Entry> entries = SpawnerData.get(job);
                if (message.action == 1) {
                    List<String> names = ServerCloneController.Instance.getClones(message.tab);
                    if (!names.contains(message.name) || entries.size() >= 4096) return;
                    int slot = entries.isEmpty() ? 1 : entries.lastKey() + 1;
                    entries.put(slot, new SpawnerData.Entry(message.tab, message.name, null));
                } else if (message.action == 2) entries.remove(message.slot);
                else if (message.action == 3) {
                    List<Integer> keys = new ArrayList<>(entries.keySet());
                    int from = keys.indexOf(message.slot), to = from + Integer.signum(message.direction);
                    if (from >= 0 && to >= 0 && to < keys.size()) {
                        SpawnerData.Entry value = entries.get(message.slot);
                        entries.put(message.slot, entries.get(keys.get(to)));
                        entries.put(keys.get(to), value);
                    }
                } else if (message.action != 0) return;
                npc.updateClient = true;
                NBTTagCompound reply = job.getTitles();
                reply.setInteger("CNPCPlusSpawnerTab", message.tab);
                NBTTagList names = new NBTTagList();
                for (String name : ServerCloneController.Instance.getClones(message.tab)) names.appendTag(new NBTTagString(name));
                reply.setTag("List", names);
                Server.sendData(player, EnumPacketClient.GUI_DATA, reply);
            });
            return null;
        }
    }
}
