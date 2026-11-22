package top.cnpcplus.transport;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.controllers.data.TransportLocation;

public final class TransportFare {
    public static final String KEY = "CnpcPlusTransportFare";
    public static final int SLOTS = 9;

    private TransportFare() {}

    public static CompoundTag of(TransportLocation location) {
        return ((TransportVisualMeta) location).cnpcplus$getFare();
    }

    public static List<ItemStack> items(CompoundTag fare) {
        List<ItemStack> items = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) items.add(ItemStack.of(fare.getCompound(Integer.toString(i))));
        return items;
    }

    public static CompoundTag save(boolean enabled, List<ItemStack> items) {
        var tag = new CompoundTag();
        if (enabled) tag.putBoolean("Enabled", true);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) tag.put(Integer.toString(i), stack.save(new CompoundTag()));
        }
        return tag;
    }

    public record Requirement(ItemStack sample, int needed, int owned) {
        public boolean affordable() { return owned >= needed; }
    }

    public static List<Requirement> requirements(Inventory inventory, List<ItemStack> costs) {
        var requirements = new ArrayList<Requirement>();
        for (int i = 0; i < costs.size(); i++) {
            var stack = costs.get(i);
            if (stack.isEmpty()) continue;
            if (costs.subList(0, i).stream().anyMatch(other -> ItemStack.isSameItemSameTags(stack, other))) continue;
            int needed = 0;
            int owned = 0;
            for (var cost : costs) {
                if (ItemStack.isSameItemSameTags(stack, cost)) needed += cost.getCount();
            }
            for (var item : inventory.items) {
                if (ItemStack.isSameItemSameTags(stack, item)) owned += item.getCount();
            }
            requirements.add(new Requirement(stack, needed, owned));
        }
        return requirements;
    }

    /** Allocate each inventory item once, including when two fee slots ask for identical items. */
    public static boolean pay(Inventory inventory, List<ItemStack> costs, boolean consume) {
        int[] allocated = new int[inventory.items.size()];
        boolean hasCost = false;
        for (ItemStack cost : costs) {
            if (cost.isEmpty()) continue;
            hasCost = true;
            int remaining = cost.getCount();
            for (int i = 0; i < allocated.length && remaining > 0; i++) {
                ItemStack stack = inventory.items.get(i);
                if (!ItemStack.isSameItemSameTags(cost, stack)) continue;
                int count = Math.min(remaining, stack.getCount() - allocated[i]);
                allocated[i] += count;
                remaining -= count;
            }
            if (remaining != 0) return false;
        }
        if (hasCost && consume) {
            for (int i = 0; i < allocated.length; i++) {
                if (allocated[i] > 0) inventory.items.get(i).shrink(allocated[i]);
            }
            inventory.setChanged();
        }
        return hasCost;
    }
}
