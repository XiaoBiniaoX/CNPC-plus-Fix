package bin.cnpcplus.station;

import bin.cnpcplus.smelting.SmeltingPermissions;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;

/** Uses authoritative ghost definition slots, like the existing recipe editors. */
public final class StationContainer extends Container {
    public final int kind, recipeSlots;
    public StationRecipe recipe = new StationRecipe();
    public final InventoryBasic definitions = new InventoryBasic("Recipe", false, 7);
    public StationContainer(EntityPlayer player, int kind) {
        this.kind = kind;
        if (kind == 0) { define(0, 49, 39); define(1, 49, 60); define(4, 129, 54); }
        else if (kind == 1) {
            define(0, 15, 68); define(1, 38, 75); define(2, 61, 68); define(3, 38, 41);
            define(4, 110, 68); define(5, 133, 75); define(6, 156, 68);
        } else { define(0, 56, 62); define(4, 116, 62); }
        recipeSlots = inventorySlots.size();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, 8 + col * 18, 113 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 171));
    }
    private void define(int index, int x, int y) {
        addSlotToContainer(new Slot(definitions, index, x, y) {
            @Override public boolean canTakeStack(EntityPlayer player) { return false; }
            @Override public boolean isItemValid(ItemStack stack) { return false; }
        });
    }
    public void select(StationRecipe data) {
        recipe = data == null ? new StationRecipe() : StationRecipe.read(data.save(true));
        for (int i = 0; i < 7; i++) definitions.setInventorySlotContents(i, (i < 4 ? recipe.inputs[i] : recipe.outputs[i - 4]).copy());
        detectAndSendChanges();
    }
    public void readSlots(StationRecipe data) {
        for (int i = 0; i < 4; i++) data.inputs[i] = definitions.getStackInSlot(i).copy();
        for (int i = 0; i < 3; i++) data.outputs[i] = definitions.getStackInSlot(4 + i).copy();
    }
    @Override public ItemStack slotClick(int slot, int button, ClickType type, EntityPlayer player) {
        if (slot >= 0 && slot < recipeSlots) {
            if (type != ClickType.PICKUP || !player.world.isRemote && !SmeltingPermissions.canEdit(player)) return ItemStack.EMPTY;
            ItemStack copy = player.inventory.getItemStack().copy();
            if (!copy.isEmpty()) copy.setCount(button == 1 ? 1 : Math.min(copy.getCount(), copy.getMaxStackSize()));
            definitions.setInventorySlotContents(getSlot(slot).getSlotIndex(), copy); detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        return super.slotClick(slot, button, type, player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
    @Override public boolean canInteractWith(EntityPlayer player) { return player.world.isRemote || SmeltingPermissions.canEdit(player); }
}
