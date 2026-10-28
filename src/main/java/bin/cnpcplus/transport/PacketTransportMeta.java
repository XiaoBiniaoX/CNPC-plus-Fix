package bin.cnpcplus.transport;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.EventHooks;
import noppes.npcs.NoppesUtilPlayer;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.api.event.RoleEvent;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import java.util.TreeMap;

public final class PacketTransportMeta implements IMessage {
    public static final int PLAYER = 0, EDITOR = 1, SAVE = 2, STATE = 3, IMAGE = 4, ACK = 5, TRAVEL = 6;
    public int npcId, action;
    public NBTTagCompound data = new NBTTagCompound();
    public PacketTransportMeta() { }
    public PacketTransportMeta(int npcId, int action, NBTTagCompound data) { this.npcId = npcId; this.action = action; this.data = data; }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(npcId); buf.writeInt(action); ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) { npcId = buf.readInt(); action = buf.readInt(); data = ByteBufUtils.readTag(buf); }

    public static EntityNPCInterface npc(EntityPlayerMP player, int id, boolean editor) {
        EntityNPCInterface npc = find(player, id);
        if (editor) {
            EntityNPCInterface editing = NoppesUtilServer.getEditingNpc(player);
            if (editing != null && editing.getEntityId() == id) npc = editing;
            // 编辑会话会在重开 GUI / 切维度时被清空（editingNpc 变 null）。
            // 以前这里直接 return null，保存包被静默丢弃 —— 表现就是"点保存没反应"。
            // 只要玩家有 NPC_ADVANCED（本来就能编辑 NPC），且 id 确实指向一个 NPC，就继续处理。
            if (npc == null || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_ADVANCED)) return null;
        } else if (npc == null || npc.getDistanceSq(player) > 64) return null;
        return npc.roleInterface instanceof RoleTransporter && !npc.isKilled() ? npc : null;
    }

    /** 先在本维度找，再扫所有维度：编辑中的 NPC 可能停在别的世界。 */
    private static EntityNPCInterface find(EntityPlayerMP player, int id) {
        Entity entity = player.world.getEntityByID(id);
        if (entity instanceof EntityNPCInterface) return (EntityNPCInterface) entity;
        for (net.minecraft.world.WorldServer world : player.getServer().worlds) {
            entity = world.getEntityByID(id);
            if (entity instanceof EntityNPCInterface) return (EntityNPCInterface) entity;
        }
        return null;
    }

    public static boolean visible(EntityPlayerMP player, TransportLocation own, TransportLocation target) {
        return target != null && own.category.locations.containsKey(target.id)
                && (target.isDefault() || PlayerData.get(player).transportData.transports.contains(target.id));
    }

    public static NBTTagCompound snapshot(EntityPlayerMP player, EntityNPCInterface npc, boolean editor) {
        NBTTagCompound tag = new NBTTagCompound();
        TransportLocation own = ((RoleTransporter) npc.roleInterface).getLocation();
        if (own == null || own.category == null) return tag;
        NBTTagList entries = new NBTTagList();
        for (TransportLocation location : new TreeMap<>(own.category.locations).values()) {
            if (editor || location.id != own.id && visible(player, own, location)) entries.appendTag(TransportData.meta(location));
        }
        tag.setTag("Entries", entries); tag.setTag("CurrentMeta", TransportData.meta(own));
        tag.setTag("Panel", TransportData.panel(own.category)); tag.setBoolean("Editor", editor);
        return tag;
    }

    public static final class Handler implements IMessageHandler<PacketTransportMeta, IMessage> {
        @Override public IMessage onMessage(PacketTransportMeta message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(message, player)); return null;
        }
        private static void handle(PacketTransportMeta message, EntityPlayerMP player) {
            if (message.data == null) return;
            boolean editor = message.action == EDITOR || message.action == SAVE || message.action == IMAGE && message.data.getBoolean("Editor");
            EntityNPCInterface npc = npc(player, message.npcId, editor);
            if (npc == null) { if (message.action == SAVE) reject(player, message, "npc not editable/found"); return; }
            TransportLocation own = ((RoleTransporter) npc.roleInterface).getLocation();
            if (own == null || own.category == null) { if (message.action == SAVE) reject(player, message, "npc has no transport location/category"); return; }
            if (message.action == PLAYER || message.action == EDITOR) {
                CraftingViewNetwork.CHANNEL.sendTo(new PacketTransportMeta(npc.getEntityId(), STATE, snapshot(player, npc, editor)), player);
            } else if (message.action == TRAVEL) {
                TransportLocation target = own.category.locations.get(message.data.getInteger("Id"));
                if (target == null) target = TransportController.getInstance().getTransport(message.data.getInteger("Id"));
                if (!visible(player, own, target)) return;
                RoleEvent.TransporterUseEvent event = new RoleEvent.TransporterUseEvent(player, npc.wrappedNPC, target);
                if (!EventHooks.onNPCRole(npc, event)) NoppesUtilPlayer.teleportPlayer(player,
                        target.pos.getX(), target.pos.getY(), target.pos.getZ(), target.dimension);
            } else if (message.action == IMAGE) {
                String key = TransportData.key(message.data.getString("Key"));
                NBTTagCompound allowed = snapshot(player, npc, editor);
                if (imageAllowed(allowed, key)) TransportImages.send(player, message.npcId, key);
            } else if (message.action == SAVE) {
                if (message.data.hasKey("Panel", 10)) TransportData.setPanel(own.category, message.data.getCompoundTag("Panel"));
                NBTTagList entries = message.data.getTagList("Entries", 10);
                java.util.List<Integer> applied = new java.util.ArrayList<>();
                boolean accepted = true;
                for (int i = 0; i < entries.tagCount() && i < 4096; i++) {
                    NBTTagCompound patch = entries.getCompoundTagAt(i);
                    int id = patch.getInteger("Id");
                    TransportLocation target = own.category.locations.get(id);
                    // 分类表里查不到就回退到全局表（旧存档、刚被移动的点位）：
                    // 以前 target == null 直接 continue，整条保存被吞掉却还回"已保存"。
                    if (target == null) target = TransportController.getInstance().getTransport(id);
                    if (target == null || target.category == null || target.category.id != own.category.id) { accepted = false; continue; }
                    if (patch.getBoolean("Delete")) {
                        if (target.id == own.id || used(player, target.id)) { accepted = false; continue; }
                        TransportController.getInstance().removeLocation(target.id);
                        applied.add(target.id);
                        continue;
                    }
                    boolean rejected = false;
                    int stampBefore = TransportData.stamp(target);
                    String name = TransportData.limit(patch.getString("Name"), 64).trim();
                    boolean renamed = !name.isEmpty() && !name.equals(target.name);
                    if (renamed) {
                        // 只跟"别的点位"比对：自己已经用着的名字（改大小写/空格）不会把自己拒掉。
                        if (nameTaken(name, target.id)) rejected = true;
                        else target.name = name;
                    }
                    TransportData.setLocation(target, patch);
                    // 名字版本号只由服务端推进：客户端带来的旧值不能把它拉低，
                    // 否则下一个打开着的原生编辑页就能拿旧名字盖回来。
                    TransportData.ensureStamp(target, stampBefore);
                    if (renamed && !rejected) TransportData.bumpNameStamp(target);
                    if (rejected) accepted = false; else applied.add(target.id);
                }
                if (message.data.getBoolean("Disk")) TransportController.getInstance().saveCategories();
                NBTTagCompound ack = new NBTTagCompound(); ack.setBoolean("Saved", accepted);
                ack.setInteger("Sequence", message.data.getInteger("Sequence"));
                ack.setIntArray("Applied", applied.stream().mapToInt(Integer::intValue).toArray());
                // 回带权威条目，客户端保存后直接用服务端结果覆盖界面（不会再假成功）。
                ack.setTag("Entries", snapshot(player, npc, editor).getTagList("Entries", 10));
                bin.cnpcplus.CnpcPlus.LOGGER.info("CNPCPlus transport save: player={} npcId={} accepted={} applied={}",
                        player.getName(), message.npcId, accepted, applied);
                CraftingViewNetwork.CHANNEL.sendTo(new PacketTransportMeta(npc.getEntityId(), ACK, ack), player);
            }
        }
        /** 保存被拒时留一条日志：下一次实测能直接从日志定位，不用再猜。 */
        private static void reject(EntityPlayerMP player, PacketTransportMeta message, String reason) {
            bin.cnpcplus.CnpcPlus.LOGGER.warn("CNPCPlus transport save dropped: player={} npcId={} reason={}",
                    player.getName(), message.npcId, reason);
        }
        /** 名字是否已被**别的**点位占用（排除自己，避免改大小写时把自己拒掉）。 */
        private static boolean nameTaken(String name, int except) {
            TransportLocation existing = TransportController.getInstance().getTransport(name);
            return existing != null && existing.id != except;
        }
        private static boolean used(EntityPlayerMP player, int id) {
            for (net.minecraft.world.WorldServer world : player.getServer().worlds) for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityNPCInterface && !entity.isDead) {
                    EntityNPCInterface npc = (EntityNPCInterface) entity;
                    if (npc.roleInterface instanceof RoleTransporter && ((RoleTransporter) npc.roleInterface).transportId == id) return true;
                }
            }
            return false;
        }
        private static boolean imageAllowed(NBTTagCompound root, String key) {
            if (key.isEmpty()) return false;
            NBTTagCompound panel = root.getCompoundTag("Panel");
            if (key.equals(panel.getString("TabIcon")) || key.equals(panel.getString("CurIcon"))) return true;
            NBTTagList entries = root.getTagList("Entries", 10);
            if (entryImage(root.getCompoundTag("CurrentMeta"), key)) return true;
            for (int i = 0; i < entries.tagCount(); i++) if (entryImage(entries.getCompoundTagAt(i), key)) return true;
            return false;
        }
        private static boolean entryImage(NBTTagCompound entry, String key) {
            return key.equals(entry.getString("Icon")) || key.equals(entry.getString("IconSel")) || key.equals(entry.getString("Image"));
        }
    }

    public static final class ClientHandler implements IMessageHandler<PacketTransportMeta, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(PacketTransportMeta message, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> bin.cnpcplus.transport.client.TransportScreen.receive(message)); return null;
        }
    }
}
