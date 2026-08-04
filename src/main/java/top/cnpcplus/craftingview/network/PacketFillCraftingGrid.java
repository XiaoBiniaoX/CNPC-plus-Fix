package top.cnpcplus.craftingview.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import top.cnpcplus.accessor.RecipeCarpentryOffsetAccessor;
import top.cnpcplus.craftingview.CraftUtils;

import java.util.function.Supplier;

public class PacketFillCraftingGrid {

    private ResourceLocation recipeId;

    public PacketFillCraftingGrid() {}

    public PacketFillCraftingGrid(ResourceLocation recipeId) {
        this.recipeId = recipeId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(recipeId);
    }

    public static PacketFillCraftingGrid decode(FriendlyByteBuf buf) {
        return new PacketFillCraftingGrid(buf.readResourceLocation());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            AbstractContainerMenu open = player.containerMenu;

            if (open instanceof ContainerCarpentryBench container) {
                fillCarpentryBench(player, container);
            } else if (open instanceof CraftingMenu container) {
                fillCraftingTable(player, container);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private void fillCarpentryBench(ServerPlayer player, ContainerCarpentryBench container) {
        RecipeCarpentry recipe = RecipeController.instance.anvilRecipes != null
                ? RecipeController.instance.anvilRecipes.get(recipeId) : null;
        if (recipe == null) return;

        int gridSize = 16;
        for (int s = 0; s < gridSize; s++) {
            ItemStack ex = container.craftMatrix.getItem(s);
            if (!ex.isEmpty()) {
                returnToInventory(player, ex);
                container.craftMatrix.setItem(s, ItemStack.EMPTY);
            }
        }

        int rw = recipe.getWidth();
        int rh = recipe.getHeight();
        int offsetX = ((RecipeCarpentryOffsetAccessor) recipe).cnpcplus$getOffsetX();
        int offsetY = ((RecipeCarpentryOffsetAccessor) recipe).cnpcplus$getOffsetY();
        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                ItemStack required = recipe.getCraftingItem(row * rw + col);
                if (required == null || required.isEmpty()) continue;

                int gridSlot = (row + offsetY) * 4 + col + offsetX;
                if (gridSlot < 0 || gridSlot >= gridSize) continue;
                ItemStack found = findAndTake(player, required, recipe.ignoreDamage, recipe.ignoreNBT);
                if (found != null) {
                    container.craftMatrix.setItem(gridSlot, found);
                }
            }
        }

        container.slotsChanged(container.craftMatrix);
        container.broadcastChanges();
    }

    private void fillCraftingTable(ServerPlayer player, CraftingMenu container) {
        RecipeCarpentry recipe = RecipeController.instance.globalRecipes != null
                ? RecipeController.instance.globalRecipes.get(recipeId) : null;
        if (recipe == null) return;

        var slots = container.craftSlots;
        int gridSize = 9;
        for (int s = 0; s < gridSize; s++) {
            ItemStack ex = slots.getItem(s);
            if (!ex.isEmpty()) {
                returnToInventory(player, ex);
                slots.setItem(s, ItemStack.EMPTY);
            }
        }

        int rw = recipe.getWidth();
        int rh = recipe.getHeight();
        int offsetX = ((RecipeCarpentryOffsetAccessor) recipe).cnpcplus$getOffsetX();
        int offsetY = ((RecipeCarpentryOffsetAccessor) recipe).cnpcplus$getOffsetY();
        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                ItemStack required = recipe.getCraftingItem(row * rw + col);
                if (required == null || required.isEmpty()) continue;

                int gridSlot = (row + offsetY) * 3 + col + offsetX;
                if (gridSlot < 0 || gridSlot >= gridSize) continue;
                ItemStack found = findAndTake(player, required, recipe.ignoreDamage, recipe.ignoreNBT);
                if (found != null) {
                    slots.setItem(gridSlot, found);
                }
            }
        }

        container.slotsChanged(slots);
        container.broadcastChanges();
    }

    private static void returnToInventory(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack, false, false);
        }
    }

    private static ItemStack findAndTake(ServerPlayer player, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        var inv = player.getInventory().items;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.get(i);
            if (stack.isEmpty()) continue;
            if (!matches(stack, required, ignoreDamage, ignoreNBT)) continue;

            ItemStack taken = stack.copy();
            taken.setCount(1);
            stack.shrink(1);
            if (stack.isEmpty()) {
                inv.set(i, ItemStack.EMPTY);
            }
            return taken;
        }
        return null;
    }

    private static boolean matches(ItemStack stack, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        return CraftUtils.matches(stack, required, ignoreDamage, ignoreNBT);
    }
}
