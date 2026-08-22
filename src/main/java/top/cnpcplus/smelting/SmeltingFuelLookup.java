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

    /**
     * 这个物品是否被「任意一条」自定义熔炼配方指定为燃料。
     *
     * <p>用于放宽燃料槽的放入限制。之前是无条件放行任何物品，副作用是打破了原版
     * 「能放进燃料槽的必然是燃料」这一前提：原版 AbstractFurnaceMenu.quickMoveStack
     * 在燃料槽分支里依赖 isFuel 判定决定物品去向，遇到「能放但不是燃料」的物品时
     * 会把它搬走却不被承认，表现为 shift+左键把物品吞掉（用户实测）。
     *
     * <p>所以这里收窄为：只放行确实被某条配方指定为燃料的物品，其余交回原版判定。
     * 与 customBurnTime 不同，本方法不要求炉内被熔炼物已匹配——玩家往空炉子里
     * 先放燃料是正常操作顺序，那时还没有输入物可匹配。
     */
    public static boolean isCustomFuel(Level level, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return false;
        if (level.isClientSide()) return false;
        for (RecipeType<? extends AbstractCookingRecipe> type : TYPES) {
            for (Recipe<?> r : level.getRecipeManager().getAllRecipesFor(type)) {
                if (r instanceof SmeltingRecipeParser.SmeltingCookingRecipe scr
                        && scr.getFuelMatcher().burnTime(stack, type) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 三种炉型，isCustomFuel 需要跨炉型查（玩家可能在任一炉子里放燃料）。 */
    private static final java.util.List<RecipeType<? extends AbstractCookingRecipe>> TYPES =
            java.util.List.of(RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING);
}
