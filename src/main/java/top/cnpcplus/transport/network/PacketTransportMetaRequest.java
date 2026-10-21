package top.cnpcplus.transport.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerTransportData;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import top.cnpcplus.transport.TransportVisualMeta;

import java.util.function.Supplier;

/**
 * 客户端→服务端：请求当前 NPC 可见传送点的扩展元数据 + 当前位置。
 * 服务端按 getScrollData 同口径计算可见列表，只回传这些条目。
 */
public class PacketTransportMetaRequest {
    private final int npcId;
    private final boolean editor;

    public PacketTransportMetaRequest(int npcId) { this(npcId, false); }
    public PacketTransportMetaRequest(int npcId, boolean editor) { this.npcId = npcId; this.editor = editor; }

    public void encode(FriendlyByteBuf buf) { buf.writeVarInt(this.npcId); buf.writeBoolean(this.editor); }

    public static PacketTransportMetaRequest decode(FriendlyByteBuf buf) {
        return new PacketTransportMetaRequest(buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.server == null || player.server.overworld() == null) return;
            EntityNPCInterface npc = null;
            for (EntityNPCInterface e : player.level().getEntitiesOfClass(EntityNPCInterface.class,
                    player.getBoundingBox().inflate(16.0, 16.0, 16.0))) {
                if (e.getId() == this.npcId) { npc = e; break; }
            }
            if (npc == null || npc.role.getType() != 4) return;
            RoleTransporter role = (RoleTransporter) npc.role;
            TransportLocation own = role.getLocation();
            if (own == null || own.category == null) return;

            CompoundTag out = new CompoundTag();
            java.util.LinkedHashMap<String, TransportLocation> visible = new java.util.LinkedHashMap<>();
            if (this.editor) {
                // 编辑页：本组全部传送点
                for (TransportLocation loc : own.category.locations.values()) {
                    visible.putIfAbsent(loc.name, loc);
                }
            } else {
                // 玩家页：默认 + 玩家已解锁
                for (TransportLocation loc : own.category.getDefaultLocations()) {
                    visible.putIfAbsent(loc.name, loc);
                }
                PlayerTransportData pd = PlayerData.get(player).transportData;
                for (int id : pd.transports) {
                    TransportLocation loc = TransportController.getInstance().getTransport(id);
                    if (loc != null && own.category.locations.containsKey(loc.id)) {
                        visible.putIfAbsent(loc.name, loc);
                    }
                }
                visible.remove(own.name);
            }

            CompoundTag entries = new CompoundTag();
            for (java.util.Map.Entry<String, TransportLocation> en : visible.entrySet()) {
                entries.put(en.getKey(), metaOf(en.getValue()));
            }
            out.put("Entries", entries);
            out.putString("Current", own.name);
            out.put("CurrentMeta", metaOf(own));
            TransportPacketHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new PacketTransportMetaResponse(out));
        });
        ctx.get().setPacketHandled(true);
    }

    static CompoundTag metaOf(TransportLocation loc) {
        CompoundTag t = new CompoundTag();
        TransportVisualMeta m = (TransportVisualMeta) (Object) loc;
        t.putString("Icon", m.cnpcplus$getIcon());
        t.putString("IconSel", m.cnpcplus$getIconSel());
        t.putString("Image", m.cnpcplus$getImage());
        t.putInt("ImageMode", m.cnpcplus$getImageMode());
        t.putString("Desc", m.cnpcplus$getDesc());
        return t;
    }
}
