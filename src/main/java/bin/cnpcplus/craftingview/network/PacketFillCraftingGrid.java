package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.craftingview.RecipeAccess;
import bin.cnpcplus.recipe.CraftUtils;
import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;

/**
 * Server: fill crafting/carpentry grid from player inventory for a recipe id.
 * Ported behavior from 1.20.1 cnpcplus PacketFillCraftingGrid.
 */
public record PacketFillCraftingGrid(ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<PacketFillCraftingGrid> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "fill_crafting_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketFillCraftingGrid> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    PacketFillCraftingGrid::recipeId,
                    PacketFillCraftingGrid::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketFillCraftingGrid msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            fill(player, msg.recipeId());
        });
    }

    private static void fill(ServerPlayer player, ResourceLocation id) {
        if (id == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        RecipeCarpentry recipe = null;
        boolean anvil = false;
        if (menu instanceof ContainerCarpentryBench) {
            recipe = RecipeAccess.getAnvil(id);
            anvil = true;
        } else if (menu instanceof CraftingMenu) {
            recipe = RecipeAccess.getGlobal(id);
            anvil = false;
        }
        if (recipe == null) return;

        // return existing craft-grid items to inventory first
        clearGridToInventory(player, menu, anvil);

        int gridW = anvil ? 4 : 3;
        int rw = Math.max(1, recipe.getWidth());
        int rh = Math.max(1, recipe.getHeight());
        RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) recipe;
        int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
        int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
        var ings = recipe.getIngredients();
        if (ings == null) return;

        Inventory inv = player.getInventory();
        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                int idx = col + row * rw;
                if (idx < 0 || idx >= ings.size()) continue;
                Ingredient ing = ings.get(idx);
                if (ing == null || ing.isEmpty()) continue;
                ItemStack[] opts = ing.getItems();
                if (opts.length == 0) continue;
                ItemStack required = opts[0];

                int mx = col + ox;
                int my = row + oy;
                if (mx < 0 || my < 0 || mx >= gridW || my >= gridW) continue;

                int slotIndex = anvil ? (1 + my * gridW + mx) : (1 + my * 3 + mx);
                if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;

                ItemStack taken = takeMatching(inv, required, recipe.ignoreDamage, recipe.ignoreNBT);
                if (!taken.isEmpty()) {
                    menu.getSlot(slotIndex).set(taken);
                }
            }
        }
        menu.broadcastChanges();
    }

    private static void clearGridToInventory(ServerPlayer player, AbstractContainerMenu menu, boolean anvil) {
        int start = 1;
        int count = anvil ? 16 : 9;
        Inventory inv = player.getInventory();
        for (int i = start; i < start + count && i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (!inv.add(stack.copy())) {
                player.drop(stack.copy(), false);
            }
            slot.set(ItemStack.EMPTY);
        }
    }

    private static ItemStack takeMatching(Inventory inv, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (s.isEmpty()) continue;
            if (!CraftUtils.matches(s, required, ignoreDamage, ignoreNBT)) continue;
            ItemStack one = s.copy();
            one.setCount(1);
            s.shrink(1);
            if (s.isEmpty()) inv.items.set(i, ItemStack.EMPTY);
            return one;
        }
        return ItemStack.EMPTY;
    }
}