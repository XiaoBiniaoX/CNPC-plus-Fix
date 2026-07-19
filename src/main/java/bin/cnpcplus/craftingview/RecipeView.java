package bin.cnpcplus.craftingview;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;

public final class RecipeView {
    public final ResourceLocation id;
    public final String name;
    public final ItemStack output;
    public final List<ItemStack> ingredients;
    public final int recipeWidth;
    public final int recipeHeight;
    public final int offsetX;
    public final int offsetY;
    public final boolean ignoreDamage;
    public final boolean ignoreNBT;

    public RecipeView(ResourceLocation id, RecipeCarpentry recipe, int offsetX, int offsetY) {
        this.id = id;
        this.name = recipe.name != null ? recipe.name : "";
        this.output = recipe.getResult() != null ? recipe.getResult().copy() : ItemStack.EMPTY;
        this.recipeWidth = Math.max(1, recipe.getWidth());
        this.recipeHeight = Math.max(1, recipe.getHeight());
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.ignoreDamage = recipe.ignoreDamage;
        this.ignoreNBT = recipe.ignoreNBT;
        this.ingredients = new ArrayList<>();
        var ings = recipe.getIngredients();
        int size = this.recipeWidth * this.recipeHeight;
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.EMPTY;
            if (ings != null && i < ings.size()) {
                Ingredient ing = ings.get(i);
                if (ing != null && !ing.isEmpty()) {
                    ItemStack[] arr = ing.getItems();
                    if (arr.length > 0) stack = arr[0].copy();
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
        if (!(o instanceof RecipeView other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}