package top.cnpcplus.smelting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 熔炼配方解析器：把「源数据 SmeltingRecipeData」解析成 Minecraft 能用的 AbstractCookingRecipe 列表。
 * 模块化：同一源数据按 allowedStations 生成 1~3 个 recipe（熔炉/高炉/烟熏炉各自是不同 RecipeType），
 * Minecraft RecipeManager 只是最终呈现层，永不直接保存 GUI 数据。
 */
public final class SmeltingRecipeParser {

    private SmeltingRecipeParser() {}

    public static final String RECIPE_GROUP = "cnpcplus_smelting";

    public static java.util.List<AbstractCookingRecipe> parse(SmeltingRecipeData data) {
        java.util.List<AbstractCookingRecipe> out = new java.util.ArrayList<>();
        if (data == null || data.input == null || data.input.isEmpty()) return out;
        if (data.output == null || data.output.isEmpty()) return out;
        Ingredient ingredient = Ingredient.of(data.input.copy());
        int cookTime = Math.max(1, Math.round(data.cookTime));
        int fastCookTime = Math.max(1, cookTime / 2);
        // 熔炉（始终注册）
        out.add(new SmeltingCookingRecipe(
                RecipeType.SMELTING,
                new ResourceLocation("cnpcplus", "smelting/" + data.id),
                RECIPE_GROUP, CookingBookCategory.MISC,
                ingredient, data.output.copy(), data.xp, cookTime, fuelFor(data, cookTime)));
        // 高炉/烟熏炉：熔炼时间减半（与原版一致）。注意燃料时长要传「未减半」的值——
        // BlastFurnaceBlockEntity / SmokerBlockEntity 的 getBurnDuration 是
        // super.getBurnDuration(stack) / 2（javap 实证），我们的 mixin 打在父类上，
        // 返回值还会被子类再除以 2。传完整 cookTime，经子类减半后正好等于本配方的 cookingTime。
        if (data.blastAllowed) {
            out.add(new SmeltingCookingRecipe(
                    RecipeType.BLASTING,
                    new ResourceLocation("cnpcplus", "blasting/" + data.id),
                    RECIPE_GROUP, CookingBookCategory.BLOCKS,
                    ingredient, data.output.copy(), data.xp, fastCookTime, fuelFor(data, fastCookTime * 2)));
        }
        if (data.smokerAllowed) {
            out.add(new SmeltingCookingRecipe(
                    RecipeType.SMOKING,
                    new ResourceLocation("cnpcplus", "smoking/" + data.id),
                    RECIPE_GROUP, CookingBookCategory.FOOD,
                    ingredient, data.output.copy(), data.xp, fastCookTime, fuelFor(data, fastCookTime * 2)));
        }
        return out;
    }

    /**
     * 通用燃料开关：开→通用燃料匹配器；关→仅指定槽1燃料（槽1为空则不可烧）。
     * burnTime 参数是「父类 getBurnDuration 应返回的值」，不一定等于该配方的 cookingTime
     * （高炉/烟熏炉的子类会再减半，见上方调用处注释）。
     */
    private static FuelMatcher fuelFor(SmeltingRecipeData data, int burnTime) {
        return data.genericFuelAllowed
                ? FuelMatcher.generic()
                : FuelMatcher.specified(data.fuel, burnTime);
    }

    /** 熔炼配方（自定义子类，携带燃料匹配器）。 */
    public static class SmeltingCookingRecipe extends AbstractCookingRecipe {
        private final FuelMatcher fuelMatcher;

        public SmeltingCookingRecipe(RecipeType<?> type, ResourceLocation id, String group,
                                     CookingBookCategory category, Ingredient ingredient,
                                     ItemStack result, float experience, int cookingTime, FuelMatcher fuel) {
            super(type, id, group, category, ingredient, result, experience, cookingTime);
            this.fuelMatcher = fuel;
        }

        /**
         * 序列化器必须与 getType() 对应。
         * 这个值会被 RecipeManager.toNetwork 写进下发给客户端的配方包，客户端按它反序列化并归类；
         * 若三种炉子都用 SMELTING_RECIPE，客户端会把高炉/烟熏配方全部当成熔炉配方，
         * 导致客户端 byType(BLASTING/SMOKING) 里没有它们、配方书出现重复条目。
         */
        @Override
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
            RecipeType<?> t = this.getType();
            if (t == RecipeType.BLASTING) return net.minecraft.world.item.crafting.RecipeSerializer.BLASTING_RECIPE;
            if (t == RecipeType.SMOKING) return net.minecraft.world.item.crafting.RecipeSerializer.SMOKING_RECIPE;
            return net.minecraft.world.item.crafting.RecipeSerializer.SMELTING_RECIPE;
        }

        public FuelMatcher getFuelMatcher() {
            return this.fuelMatcher;
        }
    }
}
