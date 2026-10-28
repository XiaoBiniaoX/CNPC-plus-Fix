package bin.cnpcplus.crafting;

import bin.cnpcplus.smelting.SmeltingPermissions;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** 定义槽只复制玩家光标中的服务端物品，不能提取配方样品。 */
public final class RecipeContainer extends Container {
    public final CraftKind kind;
    public CustomRecipe recipe;
    private final InventoryBasic definitions;

    public RecipeContainer(EntityPlayer player, CraftKind kind) {
        this.kind = kind;
        this.recipe = new CustomRecipe(kind);
        this.definitions = new InventoryBasic("Recipe", false, kind.slots.length);
        for (int i = 0; i < kind.slots.length; i++) {
            this.addSlotToContainer(new Slot(definitions, i, kind.slots[i][0], kind.slots[i][1]) {
                @Override public boolean canTakeStack(EntityPlayer player) { return false; }
                @Override public boolean isItemValid(ItemStack stack) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(player.inventory, 9 + row * 9 + col, 8 + col * 18, 113 + row * 18));
        }
        for (int col = 0; col < 9; col++) this.addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, 171));
    }

    public void select(CustomRecipe recipe) {
        this.recipe = recipe == null ? new CustomRecipe(kind) : recipe.copy();
        for (int i = 0; i < kind.inputKeys.length; i++) definitions.setInventorySlotContents(i, this.recipe.inputs[i].copy());
        definitions.setInventorySlotContents(kind.inputKeys.length, this.recipe.result.copy());
        this.detectAndSendChanges();
    }

    public void readSlots(CustomRecipe data) {
        for (int i = 0; i < data.inputs.length; i++) data.inputs[i] = definitions.getStackInSlot(i).copy();
        data.result = definitions.getStackInSlot(data.inputs.length).copy();
    }

    @Override
    public ItemStack slotClick(int slot, int button, ClickType type, EntityPlayer player) {
        if (slot >= 0 && slot < kind.slots.length) {
            if (type != ClickType.PICKUP || (!player.world.isRemote && !SmeltingPermissions.canEdit(player))) return ItemStack.EMPTY;
            ItemStack cursor = player.inventory.getItemStack();
            if (cursor.isEmpty()) definitions.setInventorySlotContents(slot, ItemStack.EMPTY);
            else {
                ItemStack copy = cursor.copy();
                copy.setCount(button == 1 ? 1 : Math.min(64, copy.getCount()));
                definitions.setInventorySlotContents(slot, copy);
            }
            this.detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        return super.slotClick(slot, button, type, player);
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canInteractWith(EntityPlayer player) { return player.world.isRemote || SmeltingPermissions.canEdit(player); }
}
