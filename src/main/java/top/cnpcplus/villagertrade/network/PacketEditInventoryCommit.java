package top.cnpcplus.villagertrade.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import bin.cnpcplus.CnpcPlus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 关面板时把编辑期背包改动（对比开面板快照的 diff）提交给服务端。
 * 编辑面板是纯客户端菜单（containerId=0，无服务端 menu），槽位点击全部本地执行——
 * 不提交则复制/移动只存在于客户端（幽灵），且旧逻辑 restoreSnapshot 关面板即回滚
 * （"复制物品失效"根因，阶段223）。单机信任模型；专用服 handle 端硬守卫拒绝，
 * 防止此包变成客户端任意改背包的作弊通道。
 */
public class PacketEditInventoryCommit implements CustomPacketPayload {
    public static final Type<PacketEditInventoryCommit> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "villager_edit_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEditInventoryCommit> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), PacketEditInventoryCommit::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    /** index: 0..35=主背包, 36..39=盔甲, 40=副手（vanilla Inventory 布局） */
    private final List<Entry> changes;

    public PacketEditInventoryCommit(List<Entry> changes) {
        this.changes = changes;
    }

    public record Entry(int index, ItemStack stack) {}

    public void encode(RegistryFriendlyByteBuf buf) {
        if (this.changes.size() > 41) throw new IllegalArgumentException("Too many inventory changes");
        buf.writeVarInt(this.changes.size());
        for (Entry e : this.changes) {
            buf.writeVarInt(e.index());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.stack());
        }
    }

    public static PacketEditInventoryCommit decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > 41) throw new IllegalArgumentException("Invalid inventory change count: " + n);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int index = buf.readVarInt();
            if (index < 0 || index > 40) throw new IllegalArgumentException("Invalid inventory slot: " + index);
            list.add(new Entry(index, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
        }
        return new PacketEditInventoryCommit(list);
    }

    public static void handle(PacketEditInventoryCommit msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            // 单机（含 LAN 主机，integrated server）才接受；专用服拒绝
            if (player.level().getServer() == null || player.level().getServer().isDedicatedServer()) {
                // 233d-UI9 日志清理：println→debug（默认不输出，只留 error 级）
                org.slf4j.LoggerFactory.getLogger("cnpcplus").debug("[vt] inv commit rejected (dedicated)");
                return;
            }
            var inv = player.getInventory();
            int armorBase = inv.items.size();
            int offhandBase = armorBase + inv.armor.size();
            for (Entry e : msg.changes) {
                int idx = e.index();
                ItemStack stack = e.stack();
                if (stack.getCount() > stack.getMaxStackSize()) {
                    stack.setCount(stack.getMaxStackSize());
                }
                if (idx >= 0 && idx < armorBase) {
                    inv.items.set(idx, stack);
                } else if (idx >= armorBase && idx < offhandBase) {
                    inv.armor.set(idx - armorBase, stack);
                } else if (idx == offhandBase && !inv.offhand.isEmpty()) {
                    inv.offhand.set(0, stack);
                }
            }
            player.inventoryMenu.broadcastChanges();
        });
    }
}
