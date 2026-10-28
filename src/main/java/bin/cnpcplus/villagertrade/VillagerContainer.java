package bin.cnpcplus.villagertrade;

import bin.cnpcplus.crafting.CustomRecipe;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 1.20.1 村民面板布局；购买和定义编辑均使用真正的服务端容器。 */
public final class VillagerContainer extends Container {
    public final EntityNPCInterface npc;
    public final boolean editing;
    public final EntityPlayer player;
    public final RoleVillagerTrader role;
    public List<TradeData> trades = new ArrayList<>();
    public int selected = -1;
    private final InventoryCrafting input = new InventoryCrafting(this, 2, 1);
    private final InventoryCraftResult output = new InventoryCraftResult();
    private final Map<Integer, Integer> uses = new HashMap<>();
    /** 上一次明确推给客户端的 0/1/2 槽位，逐 tick 比对补发。 */
    private final ItemStack[] pushed = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private boolean busy;
    private long revision = -1;

    public VillagerContainer(EntityPlayer player, EntityNPCInterface npc, boolean editing) {
        this.player = player; this.npc = npc; this.editing = editing;
        this.role = npc != null && npc.roleInterface instanceof RoleVillagerTrader ? (RoleVillagerTrader) npc.roleInterface : null;
        addSlotToContainer(new Slot(input, 0, 136, 37));
        addSlotToContainer(new Slot(input, 1, 162, 37));
        addSlotToContainer(new Slot(output, 0, 220, 37) {
            @Override public boolean isItemValid(ItemStack stack) { return false; }
            @Override public boolean canTakeStack(EntityPlayer player) { return !editing && canTrade(); }
            @Override public ItemStack onTake(EntityPlayer player, ItemStack stack) { consume(); return stack; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, 108 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, col, 108 + col * 18, 142));
        if (!player.world.isRemote && role != null) { role.ensureId(); trades = TradeStore.list(role.tradeListId); }
    }

    public TradeData current() {
        if (player.world.isRemote) return selected >= 0 && selected < trades.size() ? trades.get(selected) : null;
        return role == null ? null : TradeStore.get(role.tradeListId, selected);
    }
    private boolean exhausted(TradeData trade) { return exhausted(trade, selected); }
    /** 扫描候选条目时必须按候选下标查次数，不能拿当前 selected 的计数。 */
    private boolean exhausted(TradeData trade, int index) {
        if (player.world.isRemote) return trade.uses >= (trade.limit ? trade.limitCount : trade.maxUses);
        return trade.limit ? trade.uses >= trade.limitCount : uses.getOrDefault(index, 0) >= trade.maxUses;
    }
    private boolean paid(ItemStack have, ItemStack price, boolean nameOnly) {
        return CustomRecipe.matches(have, price, nameOnly) && (price.isEmpty() || have.getCount() >= price.getCount());
    }
    private int direction(TradeData trade) {
        if (paid(input.getStackInSlot(0), trade.a, trade.nameOnly) && paid(input.getStackInSlot(1), trade.b, trade.nameOnly)) return 0;
        if (paid(input.getStackInSlot(1), trade.a, trade.nameOnly) && paid(input.getStackInSlot(0), trade.b, trade.nameOnly)) return 1;
        return -1;
    }
    private boolean canTrade() {
        TradeData trade = current();
        return trade != null && !trade.a.isEmpty() && !trade.output.isEmpty() && !exhausted(trade)
                && direction(trade) >= 0 && ItemStack.areItemStacksEqual(output.getStackInSlot(0), trade.output);
    }

    public void select(int index) {
        if (player.world.isRemote || role == null) return;
        trades = TradeStore.list(role.tradeListId);
        selected = index >= 0 && index < trades.size() ? index : -1;
        busy = true;
        try {
            if (!editing) for (int i = 0; i < 2; i++) returnItem(input.removeStackFromSlot(i));
            TradeData trade = current();
            if (editing) {
                input.setInventorySlotContents(0, trade == null ? ItemStack.EMPTY : trade.a.copy());
                input.setInventorySlotContents(1, trade == null ? ItemStack.EMPTY : trade.b.copy());
                output.setInventorySlotContents(0, trade == null ? ItemStack.EMPTY : trade.output.copy());
            } else if (trade != null && !exhausted(trade)) {
                fill(0, trade.a, trade.nameOnly); fill(1, trade.b, trade.nameOnly);
            }
        } finally { busy = false; }
        onCraftMatrixChanged(input);
        sync();
    }

    private void fill(int index, ItemStack price, boolean nameOnly) {
        if (price.isEmpty()) return;
        ItemStack collected = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            ItemStack have = player.inventory.getStackInSlot(i);
            if (!CustomRecipe.matches(have, price, nameOnly)) continue;
            if (!collected.isEmpty() && (!ItemStack.areItemsEqual(collected, have) || !ItemStack.areItemStackTagsEqual(collected, have))) continue;
            int count = Math.min(have.getCount(), price.getCount() - collected.getCount());
            if (count <= 0) break;
            ItemStack taken = player.inventory.decrStackSize(i, count);
            if (collected.isEmpty()) collected = taken; else collected.grow(taken.getCount());
        }
        input.setInventorySlotContents(index, collected);
    }

    private void returnItem(ItemStack stack) {
        if (!stack.isEmpty() && !player.inventory.addItemStackToInventory(stack)) player.dropItem(stack, false);
    }

    private void consume() {
        if (player.world.isRemote || editing || busy) return;
        TradeData trade = current();
        if (trade == null || exhausted(trade)) return;
        int direction = direction(trade);
        if (direction < 0) return;
        busy = true;
        try {
            input.decrStackSize(direction, trade.a.getCount());
            if (!trade.b.isEmpty()) input.decrStackSize(1 - direction, trade.b.getCount());
            if (trade.limit) {
                trade.uses++;
                if (trade.uses >= trade.limitCount && trade.refreshSeconds > 0) trade.resetAt = System.currentTimeMillis() + (long) (trade.refreshSeconds * 1000);
                TradeStore.saveRow(role.tradeListId, selected, trade);
            } else uses.put(selected, uses.getOrDefault(selected, 0) + 1);
        } finally { busy = false; }
        onCraftMatrixChanged(input);
        // 交易后结果槽可能仍是同一个样品（材料还有剩），但客户端点击时已经把它清空；
        // 只比对"上次推过的值"会判定没变化而不补发，结果槽看起来就是空的幽灵。
        pushSlots(true);
        sync();
    }

    /**
     * 手动把材料放进左槽时，服务端并不知道玩家想换哪一条交易（selected 还是 -1），
     * 于是结果槽永远是空的 —— 玩家必须先点一次条目才行。这里按当前材料自动认领
     * 匹配的条目（只改选择，不替玩家从背包抓材料，避免误扣）。
     */
    private void autoSelect() {
        if (role == null) return;
        TradeData trade = current();
        if (trade != null && !exhausted(trade) && direction(trade) >= 0) return;
        for (int i = 0; i < trades.size(); i++) {
            TradeData candidate = trades.get(i);
            if (candidate.output.isEmpty() || exhausted(candidate, i)) continue;
            if (direction(candidate) >= 0) { selected = i; sync(); return; }
        }
    }

    /**
     * 购买后显式推送 0/1/2 三个槽位。1.12.2 的 detectAndSendChanges 要等下一个 tick
     * 才比较出差异，玩家连点交易期间客户端还留着已消耗的样品，看起来像幽灵物品。
     * 逐槽与"上次推过的值"比对，只有真的不同才发包，所以每 tick 调用也不会刷屏。
     */
    private void pushSlots() { pushSlots(false); }
    private void pushSlots(boolean forceResult) {
        if (!(player instanceof net.minecraft.entity.player.EntityPlayerMP)) return;
        net.minecraft.entity.player.EntityPlayerMP mp = (net.minecraft.entity.player.EntityPlayerMP) player;
        for (int i = 0; i < pushed.length && i < inventorySlots.size(); i++) {
            ItemStack now = getSlot(i).getStack();
            // 结果槽（2）在取出后强制补发：客户端已经把本地结果槽清空，
            // 服务端重算出来的仍是同一个样品，按差值比较是比不出来的。
            boolean force = forceResult && i == 2;
            if (!force && same(now, pushed[i])) continue;
            pushed[i] = now.copy();
            mp.connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(
                    windowId, i, now.copy()));
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return ItemStack.areItemStacksEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override public void onCraftMatrixChanged(IInventory inventory) {
        if (player == null || player.world.isRemote || busy || editing) return;
        autoSelect();
        TradeData trade = current();
        output.setInventorySlotContents(0, trade != null && !trade.a.isEmpty() && !exhausted(trade)
                && direction(trade) >= 0 ? trade.output.copy() : ItemStack.EMPTY);
    }

    @Override public void detectAndSendChanges() {
        if (player != null && !player.world.isRemote && !busy) {
            if (!editing) onCraftMatrixChanged(input);
            if (role != null && revision != TradeStore.revision(role.tradeListId)) sync();
        }
        super.detectAndSendChanges();
        if (player != null && !player.world.isRemote) pushSlots();
    }

    public void sync() {
        if (!(player instanceof net.minecraft.entity.player.EntityPlayerMP) || role == null) return;
        trades = TradeStore.list(role.tradeListId);
        for (int i = 0; i < trades.size(); i++) if (!trades.get(i).limit) trades.get(i).uses = uses.getOrDefault(i, 0);
        revision = TradeStore.revision(role.tradeListId);
        PacketVillageState.send(this, (net.minecraft.entity.player.EntityPlayerMP) player);
    }

    @Override public ItemStack slotClick(int slot, int button, ClickType type, EntityPlayer player) {
        if (editing && slot >= 0 && slot < 3) {
            if (type != ClickType.PICKUP || selected < 0 || !canInteractWith(player)) return ItemStack.EMPTY;
            ItemStack copy = player.inventory.getItemStack().copy();
            if (!copy.isEmpty()) copy.setCount(button == 1 ? 1 : Math.min(64, copy.getCount()));
            getSlot(slot).putStack(copy);
            if (!player.world.isRemote) {
                TradeData trade = current();
                if (trade != null) {
                    trade.a = input.getStackInSlot(0).copy(); trade.b = input.getStackInSlot(1).copy(); trade.output = output.getStackInSlot(0).copy();
                    TradeStore.saveRow(role.tradeListId, selected, trade); sync();
                }
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slot, button, type, player);
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        if (player.world.isRemote) return true;
        if (npc == null || npc.isKilled() || npc.roleInterface != role) return false;
        return editing ? NoppesUtilServer.getEditingNpc((net.minecraft.entity.player.EntityPlayerMP) player) == npc
                && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_ADVANCED) : npc.getDistanceSq(player) <= 64;
    }
    @Override public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (!player.world.isRemote && !editing) clearContainer(player, player.world, input);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (editing || index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), original = stack.copy();
        if (index == 2) {
            if (!canTrade()) return ItemStack.EMPTY;
            int space = 0;
            for (int i = 3; i < inventorySlots.size(); i++) {
                Slot destination = getSlot(i); ItemStack have = destination.getStack();
                if (have.isEmpty()) space += Math.min(stack.getMaxStackSize(), destination.getSlotStackLimit());
                else if (ItemStack.areItemsEqual(have, stack) && ItemStack.areItemStackTagsEqual(have, stack)) space += Math.max(0, stack.getMaxStackSize() - have.getCount());
            }
            if (space < stack.getCount() || !mergeItemStack(stack, 3, 39, true)) return ItemStack.EMPTY;
            slot.putStack(ItemStack.EMPTY); slot.onTake(player, original);
        } else {
            if (!mergeItemStack(stack, index < 2 ? 3 : 0, index < 2 ? 39 : 2, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
            onCraftMatrixChanged(input);
        }
        return original;
    }
}
