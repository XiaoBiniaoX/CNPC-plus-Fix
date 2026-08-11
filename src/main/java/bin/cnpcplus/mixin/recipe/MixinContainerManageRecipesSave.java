package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.CraftUtils;
import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Mixin(ContainerManageRecipes.class)
public class MixinContainerManageRecipesSave {

    @Shadow(remap = false)
    private InventoryBasic craftingMatrix;

    @Shadow(remap = false)
    public RecipeCarpentry recipe;

    @Shadow(remap = false)
    public int size;

    @Shadow(remap = false)
    public int width;

    @Inject(method = "saveRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusSaveRecipe(CallbackInfo ci) {
        ItemStack[] grid = new ItemStack[this.size];
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.craftingMatrix.getStackInSlot(i + 1);
            grid[i] = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copy();
        }
        ItemStack result = this.craftingMatrix.getStackInSlot(0);
        if (result == null) result = ItemStack.EMPTY;
        else result = result.copy();

        String keepName = (this.recipe != null && this.recipe.name != null && !this.recipe.name.isEmpty())
                ? this.recipe.name : "unnamed";
        boolean keepGlobal = this.width == 3;
        boolean keepIgnoreD = this.recipe != null && this.recipe.ignoreDamage;
        boolean keepIgnoreN = this.recipe != null && this.recipe.ignoreNBT;
        int keepId = this.recipe != null ? this.recipe.id : -1;

        int nextChar = 0;
        char[] chars = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P'};
        Map<ItemStack, Character> nameMapping = new HashMap<ItemStack, Character>();
        int firstRow = this.width, lastRow = 0, firstCol = this.width, lastCol = 0;
        boolean seenRow = false;

        for (int i = 0; i < this.width; i++) {
            boolean seenCol = false;
            for (int j = 0; j < this.width; j++) {
                ItemStack item = grid[i * this.width + j];
                if (NoppesUtilServer.IsItemStackNull(item)) continue;
                if (!seenCol && j < firstCol) firstCol = j;
                if (j > lastCol) lastCol = j;
                seenCol = true;
                Character letter = null;
                for (ItemStack mapped : nameMapping.keySet()) {
                    if (CraftUtils.matches(item, mapped, keepIgnoreD, keepIgnoreN)) {
                        letter = nameMapping.get(mapped);
                        break;
                    }
                }
                if (letter == null) {
                    letter = Character.valueOf(chars[nextChar++]);
                    nameMapping.put(item.copy(), letter);
                }
            }
            if (seenCol) {
                if (!seenRow) {
                    firstRow = i;
                    lastRow = i;
                    seenRow = true;
                } else {
                    lastRow = i;
                }
            }
        }

        if (!seenRow || nameMapping.isEmpty()) {
            RecipeCarpentry r = new RecipeCarpentry(keepName);
            if (this.recipe != null) r.copy(this.recipe);
            r.name = keepName;
            r.isGlobal = keepGlobal;
            r.id = keepId;
            this.recipe = r;
            ci.cancel();
            return;
        }

        ArrayList<Object> data = new ArrayList<Object>();
        for (int i = firstRow; i <= lastRow; i++) {
            StringBuilder row = new StringBuilder();
            for (int j = firstCol; j <= lastCol; j++) {
                ItemStack item = grid[i * this.width + j];
                if (NoppesUtilServer.IsItemStackNull(item)) {
                    row.append(' ');
                    continue;
                }
                Character letter = null;
                for (ItemStack mapped : nameMapping.keySet()) {
                    if (CraftUtils.matches(item, mapped, false, false)) {
                        letter = nameMapping.get(mapped);
                        break;
                    }
                }
                row.append(letter != null ? letter.charValue() : ' ');
            }
            data.add(row.toString());
        }
        for (Map.Entry<ItemStack, Character> e : nameMapping.entrySet()) {
            data.add(e.getValue());
            data.add(e.getKey());
        }

        RecipeCarpentry base = this.recipe != null ? this.recipe : new RecipeCarpentry(keepName);
        RecipeCarpentry saved = RecipeCarpentry.createRecipe(base, result, data.toArray());
        saved.name = keepName;
        saved.isGlobal = keepGlobal;
        saved.ignoreDamage = keepIgnoreD;
        saved.ignoreNBT = keepIgnoreN;
        saved.id = keepId;
        if (this.recipe != null) {
            saved.availability = this.recipe.availability;
        }
        ((RecipeCarpentryOffsetAccessor) saved).cnpcplusSetOffset(firstCol, firstRow, true);
        this.recipe = saved;

        this.craftingMatrix.setInventorySlotContents(0, result);
        for (int i = 0; i < grid.length; i++) {
            this.craftingMatrix.setInventorySlotContents(i + 1, grid[i].isEmpty() ? ItemStack.EMPTY : grid[i].copy());
        }

        ci.cancel();
    }

    @Inject(method = "setRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusSetRecipe(RecipeCarpentry recipe, CallbackInfo ci) {
        if (recipe == null) return;
        this.recipe = recipe;
        ItemStack result = recipe.getResult();
        this.craftingMatrix.setInventorySlotContents(0, result == null ? ItemStack.EMPTY : result.copy());
        for (int i = 0; i < this.size; i++) {
            this.craftingMatrix.setInventorySlotContents(i + 1, ItemStack.EMPTY);
        }

        RecipeCarpentryOffsetAccessor offset = (RecipeCarpentryOffsetAccessor) recipe;
        int ox = offset.cnpcplusHasSavedOffset() ? offset.cnpcplusGetOffsetX() : 0;
        int oy = offset.cnpcplusHasSavedOffset() ? offset.cnpcplusGetOffsetY() : 0;
        int rw = Math.max(1, recipe.getRecipeWidth());
        int rh = Math.max(1, recipe.getRecipeHeight());
        // width/height from ShapedRecipes

        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                int mx = col + ox;
                int my = row + oy;
                if (mx < 0 || my < 0 || mx >= this.width || my >= this.width) continue;
                int idx = col + row * rw;
                ItemStack stack = recipe.getCraftingItem(idx);
                if (stack == null) stack = ItemStack.EMPTY;
                else stack = stack.copy();
                this.craftingMatrix.setInventorySlotContents(my * this.width + mx + 1, stack);
            }
        }
        ci.cancel();
    }
}
