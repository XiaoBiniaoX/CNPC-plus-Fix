package bin.cnpcplus.smelting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;

public final class SmeltingRecipeParser {
    private SmeltingRecipeParser() {}

    public static List<AbstractCookingRecipe> parse(SmeltingRecipeData data) {
        List<AbstractCookingRecipe> result = new ArrayList<>();
        if (data == null || data.input.isEmpty() || data.output.isEmpty()) return result;
        int time = Math.max(1, Math.round(data.cookTime));
        add(result, RecipeType.SMELTING, RecipeSerializer.SMELTING_RECIPE, data, time, CookingBookCategory.MISC);
        if (data.blastAllowed) add(result, RecipeType.BLASTING, RecipeSerializer.BLASTING_RECIPE, data, Math.max(1, time / 2), CookingBookCategory.BLOCKS);
        if (data.smokerAllowed) add(result, RecipeType.SMOKING, RecipeSerializer.SMOKING_RECIPE, data, Math.max(1, time / 2), CookingBookCategory.FOOD);
        return result;
    }

    private static void add(List<AbstractCookingRecipe> result, RecipeType<?> type, RecipeSerializer<?> serializer,
                            SmeltingRecipeData data, int time, CookingBookCategory category) {
        result.add(new AbstractCookingRecipe(type, "cnpcplus_smelting", category, Ingredient.of(data.input), data.output.copy(), data.xp, time) {
            @Override public RecipeSerializer<?> getSerializer() { return serializer; }
        });
    }
}
