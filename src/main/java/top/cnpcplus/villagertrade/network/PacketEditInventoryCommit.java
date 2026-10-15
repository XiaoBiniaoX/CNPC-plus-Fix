package top.cnpcplus.villagertrade.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 关面板时把编辑期背包改动（对比开面板快照的 diff）提交给服务端。
 * 编辑面板是纯客户端菜单（containerId=0，无服务端 menu），槽位点击全部本地执行——
 * 不提交则复制/移动只存在于客户端（幽灵），且旧逻辑 restoreSnapshot 关面板即回滚
 * （"复制物品失效"根因，阶段223）。单机信任模型；专用服 handle 端硬守卫拒绝，
 * 防止此包变成客户端任意改背包的作弊通道。
 */
public class PacketEditInventoryCommit {
    /** index: 0..35=主背包, 36..39=盔甲, 40=副手（vanilla Inventory 布局） */
    private final List<Entry> changes;

    public PacketEditInventoryCommit(List<Entry> changes) {
        this.changes = changes;
    }

    public record Entry(int index, ItemStack stack) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.changes.size());
        for (Entry e : this.changes) {
            buf.writeVarInt(e.index());
            buf.writeItem(e.stack());
        }
    }

    public static PacketEditInventoryCommit decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(Math.min(n, 64));
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readItem()));
        }
        return new PacketEditInventoryCommit(list);
    }

    public static void handle(PacketEditInventoryCommit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // 单机（含 LAN 主机，integrated server）才接受；专用服拒绝
            if (player.level().getServer() == null || player.level().getServer().isDedicatedServer()) {
                System.out.println("[vt] inv commit rejected (dedicated)");
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
        ctx.get().setPacketHandled(true);
    }
}
