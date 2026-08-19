package top.cnpcplus.smelting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import top.cnpcplus.mixin.AbstractFurnaceBlockEntityAccess;

/** 原版熔炉/高炉/烟熏炉接入自定义熔炼配方时的燃料燃烧时长查询（服务端权威）。 */
public final class SmeltingFuelLookup {

    private SmeltingFuelLookup() {}

    /**
     * 当前炉子里的被熔炼物匹配到某条自定义配方时，返回该配方对给定燃料的燃烧时长（不满足规则返回 0）。
     * 没有匹配到任何自定义配方时返回 null，调用方应回退原版逻辑。
     */
    public static Integer customBurnTime(AbstractFurnaceBlockEntity furnace, ItemStack fuel) {
        if (furnace == null) return null;
        Level level = furnace.getLevel();
        if (level == null || level.isClientSide()) return null;
        RecipeType<? extends AbstractCookingRecipe> type =
                ((AbstractFurnaceBlockEntityAccess) furnace).cnpcplus$getRecipeType();
        if (type == null) return null;
        for (Recipe<?> r : level.getRecipeManager().getAllRecipesFor(type)) {
            if (r instanceof SmeltingRecipeParser.SmeltingCookingRecipe scr && scr.matches(furnace, level)) {
                return scr.getFuelMatcher().burnTime(fuel, type);
            }
        }
        return null;
    }
}
