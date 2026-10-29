package top.cnpcplus.transport.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.TransportPanelMeta;
import top.cnpcplus.transport.TransportPanelStyle;
import top.cnpcplus.transport.TransportVisualMeta;
import top.cnpcplus.transport.TransportOrder;

import java.util.function.Supplier;

/**
 * 客户端→服务端：保存传送面板编辑结果（面板样式 + 各传送点可视化键）。
 * 权限同 NPC_ADVANCED；按名定位本组传送点，写回后（disk=true）saveCategories() 落盘。
 * 241n：编辑器每次改动都发本包（disk=false，内存即时生效→客户端不再"等一会儿"）；
 *      关闭编辑器时再发一次 disk=true 落盘；支持删除条目。
 */
public class PacketTransportMetaSave {
    private final int npcId;
    private final CompoundTag panel;
    private final CompoundTag entries;
    private final boolean disk;

    public PacketTransportMetaSave(int npcId, CompoundTag panel, CompoundTag entries) {
        this(npcId, panel, entries, true);
    }

    public PacketTransportMetaSave(int npcId, CompoundTag panel, CompoundTag entries, boolean disk) {
        this.npcId = npcId;
        this.panel = panel == null ? new CompoundTag() : panel;
        this.entries = entries == null ? new CompoundTag() : entries;
        this.disk = disk;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.npcId);
        buf.writeNbt(this.panel);
        buf.writeNbt(this.entries);
        buf.writeBoolean(this.disk);
    }

    public static PacketTransportMetaSave decode(FriendlyByteBuf buf) {
        int npcId = buf.readVarInt();
        CompoundTag panel = buf.readNbt();
        CompoundTag entries = buf.readNbt();
        boolean disk = buf.readBoolean();
        return new PacketTransportMetaSave(npcId, panel, entries, disk);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (player.server == null || player.server.overworld() == null) return;
            EntityNPCInterface npc = TransportNpcLookup.find(player, this.npcId);
            if (npc == null || npc.role.getType() != 4) return;
            RoleTransporter role = (RoleTransporter) npc.role;
            TransportLocation own = role.getLocation();
            if (own == null || own.category == null) return;

            if (!this.panel.isEmpty()) {
                TransportPanelMeta style = (TransportPanelMeta) (Object) own.category;
                CompoundTag cur = style.cnpcplus$panel();
                if (cur == null) {
                    cur = new CompoundTag();
                    style.cnpcplus$setPanel(cur);
                }
                CompoundTag incoming = this.panel.copy();
                if (incoming.contains(TransportOrder.KEY)) {
                    int[] ids = incoming.getIntArray(TransportOrder.KEY);
                    if (ids.length > 4096) incoming.remove(TransportOrder.KEY);
                    else incoming.putIntArray(TransportOrder.KEY, java.util.Arrays.stream(ids)
                            .filter(own.category.locations::containsKey).distinct().toArray());
                }
                TransportPanelStyle.merge(cur, incoming);
            }

            java.util.Set<Integer> liveIds = TransportNpcLookup.liveTransportIds(player);

            for (String key : this.entries.getAllKeys()) {
                CompoundTag t = this.entries.getCompound(key);
                if (t == null) continue;
                // 241t：**按点位 id 匹配**（名字会变/可能重名，id 唯一）→ 名字只做兜底
                TransportLocation target = null;
                int wantedId = parseInt(key);
                if (wantedId >= 0) {
                    for (TransportLocation loc : own.category.locations.values()) {
                        if (loc.id == wantedId) { target = loc; break; }
                    }
                }
                if (target == null) {
                    for (TransportLocation loc : own.category.locations.values()) {
                        if (loc.name.equals(key)) { target = loc; break; }
                    }
                }
                if (target == null) {
                    continue;
                }
                if (t.getBoolean(TransportKeys.DELETE)) {
                    // 241n：删除遗留条目（NPC 本体点位、或仍有活着的 NPC 占用的点位不能删）
                    if (target != own && !liveIds.contains(target.id)) {
                        TransportController.getInstance().removeLocation(target.id);
                    }
                    continue;
                }
                TransportVisualMeta m = (TransportVisualMeta) (Object) target;
                m.cnpcplus$setIcon(iconKey(t.getString(TransportKeys.ICON)));
                m.cnpcplus$setIconSel(iconKey(t.getString(TransportKeys.ICON_SEL)));
                m.cnpcplus$setImage(iconKey(t.getString(TransportKeys.IMAGE)));
                int mode = t.getInt(TransportKeys.IMAGE_MODE);
                m.cnpcplus$setImageMode(mode < 0 || mode >= TransportKeys.MODE_COUNT
                        ? TransportKeys.MODE_FILL : mode);
                m.cnpcplus$setDesc(limit(t.getString(TransportKeys.DESC), 2048));
                m.cnpcplus$setIconSize(t.getInt(TransportKeys.ICON_SIZE));
                m.cnpcplus$setImageZoom(t.getInt(TransportKeys.IMAGE_ZOOM));
                m.cnpcplus$setTextColor(t.getInt(TransportKeys.TEXT_COLOR));
                m.cnpcplus$setTextSize(t.getInt(TransportKeys.TEXT_SIZE));
                m.cnpcplus$setImgX(t.getInt(TransportKeys.IMG_X));
                m.cnpcplus$setImgY(t.getInt(TransportKeys.IMG_Y));
                m.cnpcplus$setImgScale(t.getInt(TransportKeys.IMG_SCALE));
                m.cnpcplus$setSrcW(t.getInt(TransportKeys.SRC_W));
                m.cnpcplus$setSrcH(t.getInt(TransportKeys.SRC_H));
                m.cnpcplus$setDescSpans(t.getString(TransportKeys.DESC_SPANS));
                m.cnpcplus$setNameSpans(t.getString(TransportKeys.NAME_SPANS));
                // 241l：改名（空=不改）
                String newName = limit(t.getString(TransportKeys.NAME), 64).trim();
                if (!newName.isEmpty() && !newName.equals(target.name)) {
                    target.name = newName;
                }
            }

            if (this.disk) {
                TransportController.getInstance().saveCategories();
            }
            if (this.disk) {
                noppes.npcs.shared.common.util.LogWriter.warn(
                        "[cnpcplus] transport meta save npc=" + this.npcId
                                + " entries=" + this.entries.getAllKeys().size() + " disk=true");
            }

            // 241o：回执 —— 服务端把当前状态回传，客户端据此显示"已保存"并刷新（能看见有没有真的存进去）
            try {
                CompoundTag out = new CompoundTag();
                out.putBoolean("Saved", true);
                TransportPacketHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new PacketTransportMetaResponse(out, true));
            } catch (Throwable ignored) {
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String iconKey(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.endsWith(".png")) raw = raw.substring(0, raw.length() - 4);
        return TransportKeys.sanitize(raw);
    }

    private static String limit(String raw, int max) {
        if (raw == null) return "";
        return raw.length() > max ? raw.substring(0, max) : raw;
    }

    private static int parseInt(String raw) {
        if (raw == null) return -1;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
