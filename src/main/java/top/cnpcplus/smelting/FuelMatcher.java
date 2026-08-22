package top.cnpcplus.smelting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

/**
 * 燃料匹配器（模块化，独立于 ItemStack 使用方）。
 * 判断"这个物品是否满足某个燃料规则"，供配方允许的燃料判定使用。
 * 通用燃料：直接走 MC/Forge 燃料判定机制（ForgeHooks.getBurnTime），不写死原版燃料、不做枚举，
 * 这样其它 mod 注册的燃料也能被识别。
 */
public interface FuelMatcher {

    /** 返回该物品作为此配方燃料时的燃烧时长（刻）；不满足规则返回 0。 */
    int burnTime(ItemStack fuel, RecipeType<?> type);

    /**
     * 通用燃料：任何 MC/Forge 认定的燃料都可以烧。
     *
     * <p>只在没有指定燃料时才单独使用。开了通用燃料并不等于「只认原版燃料」——
     * 见 SmeltingRecipeParser.fuelFor 的 either() 组合。
     */
    static FuelMatcher generic() {
        return (stack, type) -> ForgeHooks.getBurnTime(stack, type);
    }

    /**
     * 「指定燃料 或 通用燃料」二者取其一可用。
     *
     * <p>通用燃料的开关不应该影响到自定义物品燃料，通用燃料哪怕为开，也要保证自定义物品燃料
     * 在本配方可用，而不是拒绝非原版 Minecraft 的燃料物品进入熔炉燃料槽。
     *
     * <p>之前的实现是三元选择（开通用就整个换成 generic()），于是指定燃料被顶掉，
     * 盔甲之类非原版燃料既进不了燃料槽、也点不着火。改成并集：先问指定燃料，
     * 命中就用配方设定的时长；没命中再退回 Forge 判定。
     */
    static FuelMatcher either(ItemStack spec, int burnTime) {
        FuelMatcher specified = specified(spec, burnTime);
        FuelMatcher generic = generic();
        return (stack, type) -> {
            int t = specified.burnTime(stack, type);
            return t > 0 ? t : generic.burnTime(stack, type);
        };
    }

    /**
     * 指定燃料：只允许指定的 ItemStack 作为燃料，其余一律不可用。
     * burnTime 由调用方给出，取值为「父类 getBurnDuration 应返回的刻数」，目的是刚好烧完一个
     * （指定燃料常常是盔甲/铁轨这类 ForgeHooks 判定为 0 的非燃料物品，
     * 不能拿 ForgeHooks 的值，否则燃烧时长为 0，永远点不着火）。
     */
    static FuelMatcher specified(ItemStack spec, int burnTime) {
        int burn = Math.max(1, burnTime);
        return (stack, type) -> {
            if (spec == null || spec.isEmpty()) return 0;
            return ItemStack.isSameItemSameTags(spec, stack) ? burn : 0;
        };
    }
}
