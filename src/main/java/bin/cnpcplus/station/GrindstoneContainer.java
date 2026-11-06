package bin.cnpcplus.station;

import bin.cnpcplus.craftingview.RecipeAvailability;
import net.minecraft.enchantment.*;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Custom recipes take priority; unmatched equipment retains ordinary repair/disenchant behavior. */
public final class GrindstoneContainer extends Container {
    private final InventoryCrafting input = new InventoryCrafting(this, 2, 1);
    private final InventoryCraftResult output = new InventoryCraftResult();
    private final EntityPlayer owner;
    private final World world;
    private final BlockPos pos;
    private StationRecipe matched;
    private boolean taking;
    private int disenchantXp;
    public GrindstoneContainer(EntityPlayer player, BlockPos pos) {
        owner = player; world = player.world; this.pos = pos;
        addSlotToContainer(new Slot(input, 0, 49, 19)); addSlotToContainer(new Slot(input, 1, 49, 40));
        addSlotToContainer(new Slot(output, 0, 129, 34) {
            @Override public boolean isItemValid(ItemStack stack) { return false; }
            @Override public boolean canTakeStack(EntityPlayer player) {
                return getHasStack() && (world.isRemote || matched == null || RecipeAvailability.allows(matched.availability, player)
                        && (player.capabilities.isCreativeMode || player.experienceLevel >= matched.experience));
            }
            @Override public ItemStack onTake(EntityPlayer player, ItemStack stack) {
                if (world.isRemote) return stack;
                if (matched != null) {
                    if (!player.capabilities.isCreativeMode) player.addExperienceLevel(-matched.experience);
                } else if (disenchantXp > 0) {
                    int xp = (disenchantXp + 1) / 2;
                    xp += world.rand.nextInt(xp);
                    while (xp > 0) { int part = EntityXPOrb.getXPSplit(xp); xp -= part; world.spawnEntity(new EntityXPOrb(world, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, part)); }
                }
                // Compute both counts before either change can replace the current recipe.
                int top = matched == null ? input.getStackInSlot(0).getCount() : matched.inputs[0].getCount();
                int bottom = matched == null ? input.getStackInSlot(1).getCount() : matched.inputs[1].getCount();
                taking = true; input.decrStackSize(0, top); input.decrStackSize(1, bottom); taking = false;
                world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_ANVIL_USE, net.minecraft.util.SoundCategory.BLOCKS, .5f, 1.5f);
                onCraftMatrixChanged(input);
                ((EntityPlayerMP)player).connection.sendPacket(new SPacketSetSlot(windowId, 2, output.getStackInSlot(0)));
                return stack;
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 142));
    }
    @Override public void onCraftMatrixChanged(IInventory inventory) {
        if (world == null || world.isRemote || taking) return;
        ItemStack a = input.getStackInSlot(0), b = input.getStackInSlot(1); ItemStack[] inputs = {a, b};
        matched = StationRecipes.find(0, inputs, owner); disenchantXp = 0;
        ItemStack result = ItemStack.EMPTY;
        if (matched != null) result = matched.outputs[0].copy();
        else if (!StationRecipes.hasMatch(0, inputs) && !(a.isEmpty() && b.isEmpty()) && a.getCount() <= 1 && b.getCount() <= 1) {
            ItemStack first = a.isEmpty() ? b : a;
            boolean pair = !a.isEmpty() && !b.isEmpty();
            boolean valid = !pair && (!first.getEnchantmentTagList().isEmpty() || first.getItem() == net.minecraft.init.Items.ENCHANTED_BOOK)
                    || pair && a.getItem() == b.getItem() && a.isItemStackDamageable();
            if (valid) {
                result = first.copy();
                Map<Enchantment, Integer> curses = new HashMap<>();
                for (ItemStack stack : inputs) for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                    if (entry.getKey().isCurse()) curses.merge(entry.getKey(), entry.getValue(), Math::max);
                    else disenchantXp += entry.getKey().getMinEnchantability(entry.getValue());
                }
                if (pair) {
                    int max = result.getMaxDamage(), durability = max - a.getItemDamage() + max - b.getItemDamage() + max * 5 / 100;
                    result.setItemDamage(Math.max(0, max - durability));
                }
                if (result.hasTagCompound()) { result.getTagCompound().removeTag("ench"); result.getTagCompound().removeTag("StoredEnchantments"); }
                if (result.getItem() == net.minecraft.init.Items.ENCHANTED_BOOK && curses.isEmpty()) {
                    ItemStack book = new ItemStack(net.minecraft.init.Items.BOOK); if (result.hasTagCompound()) book.setTagCompound(result.getTagCompound().copy()); result = book;
                }
                EnchantmentHelper.setEnchantments(curses, result);
            }
        }
        output.setInventorySlotContents(0, result); detectAndSendChanges();
    }
    @Override public ItemStack slotClick(int index, int button, ClickType type, EntityPlayer player) {
        if (!world.isRemote && index == 2) onCraftMatrixChanged(input);
        return super.slotClick(index, button, type, player);
    }
    @Override public boolean canInteractWith(EntityPlayer player) { return world.getBlockState(pos).getBlock() == StationBlocks.GRINDSTONE && player.getDistanceSq(pos) <= 64; }
    // InventoryCraftResult removes the entire recipe output, even when PICKUP_ALL requests fewer items.
    @Override public boolean canMergeSlot(ItemStack stack, Slot slot) { return slot.inventory != output; }
    @Override public void onContainerClosed(EntityPlayer player) { super.onContainerClosed(player); if (!world.isRemote) clearContainer(player, world, input); }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index == 2 && !world.isRemote) onCraftMatrixChanged(input);
        Slot slot = getSlot(index);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), original = stack.copy();
        if (index == 2) {
            int space = 0;
            for (int i = 3; i < inventorySlots.size(); i++) {
                ItemStack current = getSlot(i).getStack();
                if (current.isEmpty()) space += stack.getMaxStackSize();
                else if (ItemStack.areItemsEqual(current, stack) && ItemStack.areItemStackTagsEqual(current, stack)) space += stack.getMaxStackSize() - current.getCount();
            }
            if (space < stack.getCount() || !mergeItemStack(stack, 3, 39, true)) return ItemStack.EMPTY;
            output.setInventorySlotContents(0, ItemStack.EMPTY); slot.onTake(player, original);
        } else {
            if (!mergeItemStack(stack, index < 2 ? 3 : 0, index < 2 ? 39 : 2, false)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        }
        return original;
    }
}
