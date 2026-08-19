package bin.cnpcplus.smelting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ContainerSmeltingRecipes extends AbstractContainerMenu {
    public final SimpleContainer recipe = new SimpleContainer(3);
    public int selectedId;
    public ContainerSmeltingRecipes(int id, Inventory inventory, RegistryFriendlyByteBuf data) { this(id, inventory, data.readVarInt()); }
    public ContainerSmeltingRecipes(int id, Inventory inventory, int selectedId) {
        super(SmeltingMenus.TYPE, id);
        this.selectedId = selectedId;
        addSlot(new Slot(recipe, 1, SmeltingLayout.slotInputX(), SmeltingLayout.slotInputY()));
        addSlot(new Slot(recipe, 0, SmeltingLayout.slotFuelX(), SmeltingLayout.slotFuelY()));
        addSlot(new Slot(recipe, 2, SmeltingLayout.slotOutputX(), SmeltingLayout.slotOutputY()));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 113 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 171));
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void removed(Player player) { super.removed(player); clearContainer(player, recipe); }
    public ItemStack input() { return recipe.getItem(1); }
    public ItemStack fuel() { return recipe.getItem(0); }
    public ItemStack output() { return recipe.getItem(2); }
    public void clearRecipe() {
        selectedId = -1;
        recipe.setItem(0, ItemStack.EMPTY);
        recipe.setItem(1, ItemStack.EMPTY);
        recipe.setItem(2, ItemStack.EMPTY);
        broadcastChanges();
    }
    public void setRecipe(SmeltingRecipeData data) {
        if (data == null) return;
        selectedId = data.id;
        recipe.setItem(1, data.input.copy());
        recipe.setItem(0, data.fuel.copy());
        recipe.setItem(2, data.output.copy());
        broadcastChanges();
    }
}
