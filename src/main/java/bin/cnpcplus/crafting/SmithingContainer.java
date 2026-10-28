package bin.cnpcplus.crafting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SmithingContainer extends Container {
    private final InventoryCrafting input = new InventoryCrafting(this, 3, 1);
    private final InventoryCraftResult output = new InventoryCraftResult();
    private final World world;
    private final BlockPos pos;
    private final EntityPlayer owner;
    /** 上一次明确推给客户端的前四个槽位（0/1/2 材料，3 结果）。 */
    private final ItemStack[] pushed = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private boolean taking;
    /** 当前匹配的配方；结果槽按它的输入数量扣材料。 */
    private CustomRecipe matched;

    public SmithingContainer(EntityPlayer player, BlockPos pos) {
        this.world = player.world; this.pos = pos; this.owner = player;
        for (int i = 0; i < 3; i++) addSlotToContainer(new Slot(input, i, 8 + i * 18, 48));
        addSlotToContainer(new Slot(output, 0, 98, 48) {
            @Override public boolean isItemValid(ItemStack stack) { return false; }
            @Override public boolean canTakeStack(EntityPlayer player) { return getHasStack(); }
            @Override public ItemStack onTake(EntityPlayer player, ItemStack stack) {
                if (!world.isRemote) {
                    taking = true;
                    try {
                        for (int i = 0; i < 3; i++) {
                            int need = matched != null && !matched.inputs[i].isEmpty() ? matched.inputs[i].getCount() : 1;
                            input.decrStackSize(i, need);
                        }
                    } finally { taking = false; }
                    world.playEvent(1030, pos, 0);
                    onCraftMatrixChanged(input);
                    // 材料还有剩时结果槽会重算出同一个物品：客户端在点击瞬间已经把
                    // 本地结果槽清空，而服务端差值缓存认为"没变"，必须强制补发一次。
                    pushSlots(true);
                }
                return stack;
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 142));
    }

    @Override public void onCraftMatrixChanged(IInventory inventory) {
        if (world == null || world.isRemote || taking) return;
        ItemStack result = ItemStack.EMPTY;
        matched = null;
        for (CustomRecipe recipe : RecipeStore.list(CraftKind.SMITHING)) {
            boolean matches = true;
            for (int i = 0; i < 3; i++) {
                matches &= CustomRecipe.matches(input.getStackInSlot(i), recipe.inputs[i], recipe.nameOnly);
                if (!matches) break;
                // 配方写了数量就得凑够，否则等于免费赠送。
                if (!recipe.inputs[i].isEmpty() && input.getStackInSlot(i).getCount() < recipe.inputs[i].getCount()) {
                    matches = false;
                    break;
                }
            }
            if (matches) { matched = recipe; result = recipe.result.copy(); break; }
        }
        output.setInventorySlotContents(0, result);
        detectAndSendChanges();
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return world.getBlockState(pos).getBlock() == SmithingTable.TABLE && player.getDistanceSq(pos) <= 64;
    }

    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        pushSlots();
    }

    /**
     * 取出结果后服务端立刻按剩余材料算出**同一个**结果，detectAndSendChanges 手里的
     * 比较缓存也还是那个值，于是差值包被判定为"没变化"从不下发；而客户端在点击时
     * 已经把自己的结果槽清空了 —— 表现就是结果槽空着、点一下却还能拿走一份。
     * 这里自己记住上次同步的值，逐槽比对并补发 SPacketSetSlot，客户端一旦丢失就自愈。
     */
    private void pushSlots() { pushSlots(false); }
    private void pushSlots(boolean forceResult) {
        if (!(owner instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) owner;
        for (int i = 0; i < pushed.length && i < inventorySlots.size(); i++) {
            ItemStack now = getSlot(i).getStack();
            boolean force = forceResult && i == 3;
            if (!force && same(now, pushed[i])) continue;
            pushed[i] = now.copy();
            mp.connection.sendPacket(new SPacketSetSlot(windowId, i, now.copy()));
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return ItemStack.areItemStacksEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }
    @Override public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (!world.isRemote) clearContainer(player, world, input);
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = getSlot(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack original = slot.getStack().copy();
        ItemStack stack = slot.getStack();
        if (index == 3) {
            int space = 0;
            for (int i = 4; i < inventorySlots.size(); i++) {
                Slot target = inventorySlots.get(i);
                ItemStack current = target.getStack();
                if (current.isEmpty()) space += Math.min(target.getSlotStackLimit(), stack.getMaxStackSize());
                else if (ItemStack.areItemsEqual(current, stack) && ItemStack.areItemStackTagsEqual(current, stack)) {
                    space += Math.max(0, Math.min(target.getSlotStackLimit(), stack.getMaxStackSize()) - current.getCount());
                }
            }
            if (space < stack.getCount() || !mergeItemStack(stack, 4, 40, true)) return ItemStack.EMPTY;
            slot.putStack(ItemStack.EMPTY);
            slot.onTake(player, original);
        } else if (index < 3) {
            if (!mergeItemStack(stack, 4, 40, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        } else {
            if (!mergeItemStack(stack, 0, 3, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        }
        return original;
    }
}
