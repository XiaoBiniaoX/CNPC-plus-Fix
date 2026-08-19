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

    /** 通用燃料：任何 MC/Forge 认定的燃料都可以烧。 */
    static FuelMatcher generic() {
        return (stack, type) -> ForgeHooks.getBurnTime(stack, type);
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
