package bin.cnpcplus.transport;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentTranslation;
import noppes.npcs.EventHooks;
import noppes.npcs.NoppesUtilPlayer;
import noppes.npcs.api.event.RoleEvent;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTransporter;

/** Departure location owns the fare; all destinations at that NPC use its nine slots. */
public final class TransportTrade {
    private TransportTrade() { }

    public static NBTTagCompound clean(NBTTagCompound source) {
        NBTTagCompound result = new NBTTagCompound();
        result.setBoolean("TradeEnabled", source.getBoolean("TradeEnabled"));
        NBTTagList items = new NBTTagList();
        for (ItemStack stack : costs(source)) {
            if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
            items.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        result.setTag("TradeItems", items);
        return result;
    }

    public static ItemStack[] costs(NBTTagCompound source) {
        ItemStack[] items = new ItemStack[9];
        NBTTagList list = source.getTagList("TradeItems", 10);
        for (int i = 0; i < items.length; i++) items[i] = i < list.tagCount() ? new ItemStack(list.getCompoundTagAt(i)) : ItemStack.EMPTY;
        return items;
    }

    /** Reserve counts across the whole inventory before consuming, including duplicate costs. */
    public static int[] payment(EntityPlayer player, NBTTagCompound fare) {
        int[] take = new int[player.inventory.mainInventory.size()];
        if (!fare.getBoolean("TradeEnabled")) return take;
        for (ItemStack cost : costs(fare)) {
            int remaining = cost.getCount();
            for (int slot = 0; !cost.isEmpty() && remaining > 0 && slot < take.length; slot++) {
                ItemStack stack = player.inventory.mainInventory.get(slot);
                if (!ItemStack.areItemsEqual(cost, stack) || !ItemStack.areItemStackTagsEqual(cost, stack)) continue;
                int amount = Math.min(remaining, stack.getCount() - take[slot]);
                take[slot] += amount;
                remaining -= amount;
            }
            if (remaining > 0) return null;
        }
        return take;
    }

    public static boolean travel(EntityPlayerMP player, EntityNPCInterface npc, TransportLocation target, NBTTagCompound confirmedFare) {
        TransportLocation own = ((RoleTransporter)npc.roleInterface).getLocation();
        if (own == null || target == null || target.id == own.id || !PacketTransportMeta.visible(player, own, target)
                || player.world != npc.world || npc.isKilled() || npc.getDistanceSq(player) > 64) return false;
        NBTTagCompound fare = clean(TransportData.location(own));
        if (fare.getBoolean("TradeEnabled") && (confirmedFare == null || !fare.equals(confirmedFare))) return false;
        if (payment(player, fare) == null) {
            player.sendMessage(new TextComponentTranslation("cnpcplus.transport.trade.missing"));
            return false;
        }
        if (EventHooks.onNPCRole(npc, new RoleEvent.TransporterUseEvent(player, npc.wrappedNPC, target))) return false;
        int[] take = payment(player, fare);
        if (take == null || player.getServer().getWorld(target.dimension) == null) return false;
        List<ItemStack> paid = new ArrayList<>();
        for (int slot = 0; slot < take.length; slot++) if (take[slot] > 0) paid.add(player.inventory.decrStackSize(slot, take[slot]));
        boolean moved = false;
        try {
            NoppesUtilPlayer.teleportPlayer(player, target.pos.getX(), target.pos.getY(), target.pos.getZ(), target.dimension);
            moved = player.dimension == target.dimension && player.getDistanceSq(target.pos.getX(), target.pos.getY(), target.pos.getZ()) < 1;
            return moved;
        } finally {
            if (!moved) for (ItemStack stack : paid) if (!player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }
    }
}
