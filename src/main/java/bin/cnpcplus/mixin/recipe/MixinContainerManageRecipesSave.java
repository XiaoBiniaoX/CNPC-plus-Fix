package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.NoppesUtilPlayer;
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

/**
 * saveRecipe: crop + offset + keep name; isGlobal from width==3.
 * setRecipe: apply offset placement.
 */
@Mixin(ContainerManageRecipes.class)
public class MixinContainerManageRecipesSave {

    @Shadow(remap = false)
    private SimpleContainer craftingMatrix;

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
            ItemStack stack = this.craftingMatrix.getItem(i + 1);
            grid[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        ItemStack result = this.craftingMatrix.getItem(0).copy();
        String keepName = (this.recipe != null && this.recipe.name != null && !this.recipe.name.isEmpty())
                ? this.recipe.name : "unnamed";
        // P0: width is source of truth for global vs anvil
        boolean keepGlobal = this.width == 3;
        boolean keepIgnoreD = this.recipe != null && this.recipe.ignoreDamage;
        boolean keepIgnoreN = this.recipe != null && this.recipe.ignoreNBT;

        int nextChar = 0;
        char[] chars = new char[]{'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P'};
        Map<ItemStack, Character> nameMapping = new HashMap<>();
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
                    if (NoppesUtilPlayer.compareItems(mapped, item, keepIgnoreD, keepIgnoreN)) {
                        letter = nameMapping.get(mapped);
                        break;
                    }
                }
                if (letter == null) {
                    letter = chars[nextChar++];
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
            this.recipe = r;
            CnpcPlus.LOGGER.info("[ContainerSave] empty pattern name={} width={} isGlobal={}", keepName, this.width, keepGlobal);
            ci.cancel();
            return;
        }

        ArrayList<Object> data = new ArrayList<>();
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
                    if (NoppesUtilPlayer.compareItems(mapped, item, false, false)) {
                        letter = nameMapping.get(mapped);
                        break;
                    }
                }
                row.append(letter != null ? letter : ' ');
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
        if (this.recipe != null) {
            saved.availability = this.recipe.availability;
        }
        ((RecipeCarpentryOffsetAccessor) saved).cnpcplusSetOffset(firstCol, firstRow, true);
        this.recipe = saved;

        this.craftingMatrix.setItem(0, result);
        for (int i = 0; i < grid.length; i++) {
            this.craftingMatrix.setItem(i + 1, grid[i].copy());
        }

        int ings = saved.getIngredients() != null ? saved.getIngredients().size() : -1;
        boolean resultEmpty = saved.getResult() == null || saved.getResult().isEmpty();
        CnpcPlus.LOGGER.info("[ContainerSave] name={} width={} isGlobal={} offset={},{} patternedW={} patternedH={} resultEmpty={} ings={}",
                saved.name, this.width, saved.isGlobal, firstCol, firstRow,
                saved.getWidth(), saved.getHeight(), resultEmpty, ings);
        ci.cancel();
    }

    @Inject(method = "setRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusSetRecipe(RecipeCarpentry recipe, RegistryAccess access, CallbackInfo ci) {
        if (recipe == null) return;
        this.recipe = recipe;
        this.craftingMatrix.setItem(0, recipe.getResultItem(access));
        for (int i = 0; i < this.size; i++) {
            this.craftingMatrix.setItem(i + 1, ItemStack.EMPTY);
        }

        RecipeCarpentryOffsetAccessor offset = (RecipeCarpentryOffsetAccessor) recipe;
        int ox = offset.cnpcplusHasSavedOffset() ? offset.cnpcplusGetOffsetX() : 0;
        int oy = offset.cnpcplusHasSavedOffset() ? offset.cnpcplusGetOffsetY() : 0;
        int rw = Math.max(1, recipe.getWidth());
        int rh = Math.max(1, recipe.getHeight());
        var ings = recipe.getIngredients();

        for (int row = 0; row < rh; row++) {
            for (int col = 0; col < rw; col++) {
                int mx = col + ox;
                int my = row + oy;
                if (mx < 0 || my < 0 || mx >= this.width || my >= this.width) continue;
                int idx = col + row * rw;
                ItemStack stack = ItemStack.EMPTY;
                if (ings != null && idx >= 0 && idx < ings.size()) {
                    var ing = ings.get(idx);
                    if (ing != null && !ing.isEmpty()) {
                        ItemStack[] arr = ing.getItems();
                        if (arr.length > 0) stack = arr[0].copy();
                    }
                }
                this.craftingMatrix.setItem(my * this.width + mx + 1, stack);
            }
        }
        ci.cancel();
    }
}