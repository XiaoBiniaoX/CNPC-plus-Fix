package bin.cnpcplus.craftingview;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;

public final class RecipeView {
    public final int id;
    public final String name;
    public final ItemStack output;
    public final List<ItemStack> ingredients;
    public final int recipeWidth;
    public final int recipeHeight;
    public final int offsetX;
    public final int offsetY;
    public final boolean ignoreDamage;
    public final boolean ignoreNBT;

    public RecipeView(int id, RecipeCarpentry recipe, int offsetX, int offsetY) {
        this.id = id;
        this.name = recipe.name != null ? recipe.name : "";
        ItemStack result = recipe.getResult();
        this.output = result != null && !result.isEmpty() ? result.copy() : ItemStack.EMPTY;
        this.recipeWidth = Math.max(1, recipeWidthOf(recipe));
        this.recipeHeight = Math.max(1, recipeHeightOf(recipe));
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.ignoreDamage = recipe.ignoreDamage;
        this.ignoreNBT = recipe.ignoreNBT;
        this.ingredients = new ArrayList<ItemStack>();
        NonNullList<Ingredient> ings = recipe.getIngredients();
        int size = this.recipeWidth * this.recipeHeight;
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.EMPTY;
            if (ings != null && i < ings.size()) {
                Ingredient ing = ings.get(i);
                if (ing != null) {
                    ItemStack[] arr = ing.getMatchingStacks();
                    if (arr != null && arr.length > 0 && arr[0] != null) {
                        stack = arr[0].copy();
                    }
                }
            }
            this.ingredients.add(stack);
        }
    }

    public ItemStack getRecipeOutput() {
        return output;
    }

    public ItemStack getIngredient(int index) {
        if (index < 0 || index >= ingredients.size()) return ItemStack.EMPTY;
        ItemStack s = ingredients.get(index);
        return s == null ? ItemStack.EMPTY : s;
    }

    public ItemStack getCraftingItem(int index) {
        return getIngredient(index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecipeView)) return false;
        RecipeView other = (RecipeView) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    private static int recipeWidthOf(RecipeCarpentry recipe) {
        return recipe.getRecipeWidth();
    }

    private static int recipeHeightOf(RecipeCarpentry recipe) {
        return recipe.getRecipeHeight();
    }
}
