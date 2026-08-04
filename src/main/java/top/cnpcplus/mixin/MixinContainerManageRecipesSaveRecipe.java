package top.cnpcplus.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.accessor.RecipeCarpentryOffsetAccessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(ContainerManageRecipes.class)
public class MixinContainerManageRecipesSaveRecipe {

    @Shadow(remap = false)
    private SimpleContainer craftingMatrix;

    @Shadow(remap = false)
    public RecipeCarpentry recipe;

    @Shadow(remap = false)
    public int size;

    @Shadow(remap = false)
    public int width;

    @Unique
    private ItemStack[] cnpcplus$gridBeforeSave;

    @Inject(method = "saveRecipe", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$captureGridBeforeSave(CallbackInfo ci) {
        this.cnpcplus$gridBeforeSave = new ItemStack[this.size];
        for (int i = 0; i < this.size; i++) {
            ItemStack stack = this.craftingMatrix.getItem(i + 1);
            this.cnpcplus$gridBeforeSave[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        this.cnpcplus$saveRecipeExact(this.cnpcplus$gridBeforeSave);
        this.cnpcplus$restoreGrid(this.cnpcplus$gridBeforeSave);
        this.cnpcplus$gridBeforeSave = null;
        ci.cancel();
    }

    @Inject(method = "saveRecipe", at = @At("RETURN"), remap = false)
    private void cnpcplus$restoreGridAfterSave(CallbackInfo ci) {
        if (this.cnpcplus$gridBeforeSave == null) return;
        this.cnpcplus$saveOffsetFromGrid(this.recipe, this.cnpcplus$gridBeforeSave);
        this.cnpcplus$restoreGrid(this.cnpcplus$gridBeforeSave);
        this.cnpcplus$gridBeforeSave = null;
    }

    @Unique
    private void cnpcplus$saveRecipeExact(ItemStack[] grid) {
        int firstRow = this.width;
        int lastRow = 0;
        int firstColumn = this.width;
        int lastColumn = 0;
        boolean seen = false;

        for (int row = 0; row < this.width; row++) {
            for (int col = 0; col < this.width; col++) {
                ItemStack item = grid[row * this.width + col];
                if (item.isEmpty()) continue;
                seen = true;
                if (row < firstRow) firstRow = row;
                if (row > lastRow) lastRow = row;
                if (col < firstColumn) firstColumn = col;
                if (col > lastColumn) lastColumn = col;
            }
        }

        // Tab decides type: width 3 = 工作台(global), width 4 = 木工台(anvil). Never infer from pattern size.
        boolean global = this.width == 3;

        if (!seen) {
            ResourceLocation id = this.recipe.getId();
            if (id == null) id = top.cnpcplus.persist.RecipeIds.fresh();
            RecipeCarpentry empty = new RecipeCarpentry(id, this.recipe.name);
            empty.copy(this.recipe);
            empty.isGlobal = global;
            this.recipe = empty;
            return;
        }

        char[] chars = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P'};
        Map<ItemStack, Character> mapping = new LinkedHashMap<>();
        ArrayList<Object> recipeData = new ArrayList<>();

        for (int row = firstRow; row <= lastRow; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = firstColumn; col <= lastColumn; col++) {
                ItemStack item = grid[row * this.width + col];
                if (item.isEmpty()) {
                    line.append(' ');
                    continue;
                }
                Character letter = this.cnpcplus$getExactMappedLetter(mapping, item);
                if (letter == null) {
                    letter = chars[mapping.size()];
                    mapping.put(item.copy(), letter);
                }
                line.append(letter.charValue());
            }
            recipeData.add(line.toString());
        }

        for (Map.Entry<ItemStack, Character> entry : mapping.entrySet()) {
            recipeData.add(entry.getValue());
            recipeData.add(entry.getKey());
        }

        String name = this.recipe.name;
        boolean ignoreDamage = this.recipe.ignoreDamage;
        boolean ignoreNBT = this.recipe.ignoreNBT;
        RecipeCarpentry saved = RecipeCarpentry.createRecipe(this.recipe.getId(), this.recipe, this.craftingMatrix.getItem(0), recipeData.toArray());
        saved.name = name;
        saved.isGlobal = global;
        saved.ignoreDamage = ignoreDamage;
        saved.ignoreNBT = ignoreNBT;
        this.recipe = saved;
        this.cnpcplus$saveOffsetFromGrid(this.recipe, grid);
    }

    @Unique
    private Character cnpcplus$getExactMappedLetter(Map<ItemStack, Character> mapping, ItemStack item) {
        for (Map.Entry<ItemStack, Character> entry : mapping.entrySet()) {
            ItemStack mapped = entry.getKey();
            if (mapped.getItem() == item.getItem()
                    && mapped.getDamageValue() == item.getDamageValue()
                    && ItemStack.isSameItemSameTags(mapped, item)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Inject(method = "setRecipe", at = @At("RETURN"), remap = false)
    private void cnpcplus$applyOffsetWhenOpening(RecipeCarpentry recipe, RegistryAccess access, CallbackInfo ci) {
        RecipeCarpentryOffsetAccessor offset = (RecipeCarpentryOffsetAccessor) recipe;
        if (!offset.cnpcplus$hasSavedOffset()) return;

        this.craftingMatrix.setItem(0, recipe.getResult());
        for (int i = 0; i < this.size; i++) {
            this.craftingMatrix.setItem(i + 1, ItemStack.EMPTY);
        }

        int offsetX = offset.cnpcplus$getOffsetX();
        int offsetY = offset.cnpcplus$getOffsetY();
        for (int row = 0; row < recipe.getHeight(); row++) {
            for (int col = 0; col < recipe.getWidth(); col++) {
                int matrixX = col + offsetX;
                int matrixY = row + offsetY;
                if (matrixX < 0 || matrixY < 0 || matrixX >= this.width || matrixY >= this.width) continue;
                this.craftingMatrix.setItem(matrixY * this.width + matrixX + 1, recipe.getCraftingItem(row * recipe.getWidth() + col));
            }
        }
    }

    @Unique
    private void cnpcplus$saveOffsetFromGrid(RecipeCarpentry recipe, ItemStack[] grid) {
        int firstRow = this.width;
        int firstColumn = this.width;
        for (int row = 0; row < this.width; row++) {
            for (int col = 0; col < this.width; col++) {
                ItemStack stack = grid[row * this.width + col];
                if (stack.isEmpty()) continue;
                if (row < firstRow) firstRow = row;
                if (col < firstColumn) firstColumn = col;
            }
        }
        if (firstRow == this.width || firstColumn == this.width) return;
        ((RecipeCarpentryOffsetAccessor) recipe).cnpcplus$setOffset(firstColumn, firstRow, true);
    }

    @Unique
    private void cnpcplus$restoreGrid(ItemStack[] grid) {
        for (int i = 0; i < grid.length; i++) {
            this.craftingMatrix.setItem(i + 1, grid[i].copy());
        }
    }
}
