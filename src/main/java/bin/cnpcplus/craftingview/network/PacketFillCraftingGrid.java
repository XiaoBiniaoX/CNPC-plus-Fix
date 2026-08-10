package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.craftingview.RecipeAccess;
import bin.cnpcplus.recipe.CraftUtils;
import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.data.RecipeCarpentry;

public class PacketFillCraftingGrid implements IMessage {
    private int recipeId;

    public PacketFillCraftingGrid() {}

    public PacketFillCraftingGrid(int recipeId) {
        this.recipeId = recipeId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        recipeId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(recipeId);
    }

    public static class Handler implements IMessageHandler<PacketFillCraftingGrid, IMessage> {
        @Override
        public IMessage onMessage(final PacketFillCraftingGrid msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    fill(player, msg.recipeId);
                }
            });
            return null;
        }
    }

    private static void fill(EntityPlayerMP player, int id) {
        Container menu = player.openContainer;
        if (menu == null) return;

        RecipeCarpentry recipe = null;
        boolean anvil = false;
        if (menu instanceof ContainerCarpentryBench) {
            recipe = RecipeAccess.getAnvil(id);
            anvil = true;
        } else if (menu instanceof ContainerWorkbench) {
            recipe = RecipeAccess.getGlobal(id);
            anvil = false;
        }
        if (recipe == null) return;

        clearGridToInventory(player, menu, anvil);

        int gridW = anvil ? 4 : 3;
        int rw = Math.max(1, recipe.getRecipeWidth());
        int rh = Math.max(1, recipe.getRecipeHeight());
        // pattern size
        RecipeCarpentryOffsetAccessor off = (RecipeCarpentryOffsetAccessor) recipe;
        int ox = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetX() : 0;
        int oy = off.cnpcplusHasSavedOffset() ? off.cnpcplusGetOffsetY() : 0;
        NonNullList<Ingredient> ings = recipe.getIngredients();
        if (ings == null) return;

        InventoryPlayer inv = player.inventory;
        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                int idx = col + row * rw;
                if (idx < 0 || idx >= ings.size()) continue;
                Ingredient ing = ings.get(idx);
                if (ing == null) continue;
                ItemStack[] opts = ing.getMatchingStacks();
                if (opts == null || opts.length == 0) continue;
                ItemStack required = opts[0];

                int mx = col + ox;
                int my = row + oy;
                if (mx < 0 || my < 0 || mx >= gridW || my >= gridW) continue;

                int slotIndex = anvil ? (1 + my * gridW + mx) : (1 + my * 3 + mx);
                if (slotIndex < 0 || slotIndex >= menu.inventorySlots.size()) continue;

                ItemStack taken = takeMatching(inv, required, recipe.ignoreDamage, recipe.ignoreNBT);
                if (!taken.isEmpty()) {
                    menu.getSlot(slotIndex).putStack(taken);
                }
            }
        }
        menu.detectAndSendChanges();
    }

    private static void clearGridToInventory(EntityPlayerMP player, Container menu, boolean anvil) {
        int start = 1;
        int count = anvil ? 16 : 9;
        InventoryPlayer inv = player.inventory;
        for (int i = start; i < start + count && i < menu.inventorySlots.size(); i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;
            if (!inv.addItemStackToInventory(stack.copy())) {
                player.dropItem(stack.copy(), false);
            }
            slot.putStack(ItemStack.EMPTY);
        }
    }

    private static ItemStack takeMatching(InventoryPlayer inv, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s == null || s.isEmpty()) continue;
            if (!CraftUtils.matches(s, required, ignoreDamage, ignoreNBT)) continue;
            ItemStack one = s.copy();
            one.setCount(1);
            s.shrink(1);
            if (s.isEmpty()) inv.setInventorySlotContents(i, ItemStack.EMPTY);
            return one;
        }
        return ItemStack.EMPTY;
    }
}
