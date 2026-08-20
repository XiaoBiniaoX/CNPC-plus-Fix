package bin.cnpcplus.smelting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * The three recipe slots plus the player inventory.
 *
 * The recipe slots are a *definition*, not storage. Items the player drags in are
 * real, so they are handed back when the window closes; the recipe itself is
 * already saved in the registry and does not depend on these slots.
 */
public class ContainerSmeltingRecipes extends Container {
    /** Indices into the backing inventory. */
    private static final int FUEL = 0;
    private static final int INPUT = 1;
    private static final int OUTPUT = 2;

    /**
     * Positions in the container's slot list, used by the GUI to read back real
     * slot coordinates when drawing slot backgrounds. Note this is a different
     * ordering from the indices above: slots are added input, fuel, output. The
     * mismatch is deliberate and matches the higher versions.
     */
    public static final int SLOT_INDEX_INPUT = 0;
    public static final int SLOT_INDEX_FUEL = 1;
    public static final int SLOT_INDEX_OUTPUT = 2;

    private final IInventory recipe = new InventoryBasic("Recipe", false, 3);
    private int selectedId;

    public ContainerSmeltingRecipes(InventoryPlayer playerInventory, int selectedId) {
        this.selectedId = selectedId;
        SmeltingRecipeData data = SmeltingRecipeRegistry.get(selectedId);
        if (data != null) {
            this.recipe.setInventorySlotContents(INPUT, data.input.copy());
            this.recipe.setInventorySlotContents(FUEL, data.fuel.copy());
            this.recipe.setInventorySlotContents(OUTPUT, data.output.copy());
        }
        // Layout coordinates only. The whole-panel offset is applied once by the
        // GUI to guiLeft/guiTop; adding it here too would double it.
        this.addSlotToContainer(new Slot(this.recipe, INPUT,
                SmeltingLayout.SLOT_INPUT_X, SmeltingLayout.SLOT_INPUT_Y));
        this.addSlotToContainer(new Slot(this.recipe, FUEL,
                SmeltingLayout.SLOT_FUEL_X, SmeltingLayout.SLOT_FUEL_Y));
        this.addSlotToContainer(new Slot(this.recipe, OUTPUT,
                SmeltingLayout.SLOT_OUTPUT_X, SmeltingLayout.SLOT_OUTPUT_Y));
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlotToContainer(new Slot(playerInventory, j + i * 9 + 9,
                        SmeltingLayout.INV_X + j * 18, SmeltingLayout.INV_Y + i * 18));
            }
        }
        for (int j = 0; j < 9; ++j) {
            this.addSlotToContainer(new Slot(playerInventory, j,
                    SmeltingLayout.INV_X + j * 18, SmeltingLayout.HOTBAR_Y));
        }
    }

    public int getSelectedId() {
        return this.selectedId;
    }

    public ItemStack getInput() {
        return this.recipe.getStackInSlot(INPUT);
    }

    public ItemStack getFuel() {
        return this.recipe.getStackInSlot(FUEL);
    }

    public ItemStack getOutput() {
        return this.recipe.getStackInSlot(OUTPUT);
    }

    /** Server side only: fill the slots from a recipe and tell the client. */
    public void setRecipe(SmeltingRecipeData data) {
        if (data == null) {
            this.clearRecipe(-1);
            return;
        }
        this.selectedId = data.id;
        this.recipe.setInventorySlotContents(INPUT, data.input.copy());
        this.recipe.setInventorySlotContents(FUEL, data.fuel.copy());
        this.recipe.setInventorySlotContents(OUTPUT, data.output.copy());
        this.detectAndSendChanges();
    }

    /**
     * Server side only. Clearing must never be done locally on the client: the
     * client copy would drift from the server's and items the player then drops
     * in would vanish.
     */
    public void clearRecipe(int newSelectedId) {
        this.selectedId = newSelectedId;
        this.recipe.setInventorySlotContents(INPUT, ItemStack.EMPTY);
        this.recipe.setInventorySlotContents(FUEL, ItemStack.EMPTY);
        this.recipe.setInventorySlotContents(OUTPUT, ItemStack.EMPTY);
        this.detectAndSendChanges();
    }

    /** No block or entity behind this window, so it never goes out of range. */
    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }

    /**
     * Shift-click moving is disabled. The recipe slots are a definition rather
     * than storage, and letting vanilla shuffle stacks between the two
     * inventories duplicates or destroys items. Returns EMPTY, never null:
     * vanilla dereferences this result (the quest slot crash in item 1).
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * The recipe slots hold a definition, not the player's belongings: the
     * constructor copies them out of the registry and saving only reads them.
     * Handing them back on close would mint items out of nothing, so they are
     * simply discarded.
     */
    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        for (int i = 0; i < this.recipe.getSizeInventory(); ++i) {
            this.recipe.setInventorySlotContents(i, ItemStack.EMPTY);
        }
    }

    public int slotRenderX(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.inventorySlots.size()
                ? this.inventorySlots.get(slotIndex).xPos : 0;
    }

    public int slotRenderY(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.inventorySlots.size()
                ? this.inventorySlots.get(slotIndex).yPos : 0;
    }
}
