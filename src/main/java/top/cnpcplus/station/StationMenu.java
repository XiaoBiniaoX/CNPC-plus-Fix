package top.cnpcplus.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class StationMenu extends AbstractContainerMenu {
    public static MenuType<StationMenu> TYPE;
    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.MENU.key(), helper -> {
            TYPE = new MenuType<>((IContainerFactory<StationMenu>)StationMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
            helper.register(ResourceLocation.parse("cnpcplus:station_recipes"), TYPE);
        });
    }
    public final int kind;
    public final int recipeSlots;
    public final SimpleContainer recipe = new SimpleContainer(7);
    private final boolean[] preview = new boolean[7];
    public int selected = -1;
    public StationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) { this(id, inventory, data.readVarInt()); }
    public StationMenu(int id, Inventory inventory, int kind) {
        super(TYPE, id);
        this.kind = kind;
        if (kind == 0) {
            addSlot(new Slot(recipe, 0, 49, 39));
            addSlot(new Slot(recipe, 1, 49, 60));
            addSlot(new Slot(recipe, 4, 129, 54));
        } else if (kind == 1) {
            addSlot(new Slot(recipe, 0, 15, 68));
            addSlot(new Slot(recipe, 1, 38, 75));
            addSlot(new Slot(recipe, 2, 61, 68));
            addSlot(new Slot(recipe, 3, 38, 41));
            addSlot(new Slot(recipe, 4, 110, 68));
            addSlot(new Slot(recipe, 5, 133, 75));
            addSlot(new Slot(recipe, 6, 156, 68));
        } else {
            addSlot(new Slot(recipe, 0, 56, 62));
            addSlot(new Slot(recipe, 4, 116, 62));
        }
        recipeSlots = slots.size();
        for (int i = 0; i < 3; i++) for (int j = 0; j < 9; j++) addSlot(new Slot(inventory, 9 + i * 9 + j, 8 + j * 18, 113 + i * 18));
        for (int j = 0; j < 9; j++) addSlot(new Slot(inventory, j, 8 + j * 18, 171));
    }
    public void select(int id, Player player) {
        StationRecipe data = StationRecipes.catalogue(kind).get(id);
        for (int i = 0; i < 7; i++) {
            ItemStack old = recipe.getItem(i);
            if (!preview[i] && !old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
            recipe.setItem(i, data == null ? ItemStack.EMPTY : (i < 4 ? data.inputs[i] : data.outputs[i - 4]).copy());
            preview[i] = data != null;
        }
        selected = id;
        broadcastChanges();
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        super.clicked(slot, button, type, player);
        if (slot >= 0 && slot < recipeSlots) preview[getSlot(slot).getContainerSlot()] = false;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) select(-1, player);
    }
    @Override public boolean stillValid(Player player) { return player.hasPermissions(2); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
