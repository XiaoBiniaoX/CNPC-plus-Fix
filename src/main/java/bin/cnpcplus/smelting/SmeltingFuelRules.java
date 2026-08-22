package bin.cnpcplus.smelting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** 自定义配方燃料规则；没有命中自定义配方时返回 null，交给原版。 */
public final class SmeltingFuelRules {
    private SmeltingFuelRules() {}

    /**
     * 计算自定义配方下该燃料的燃烧时长。
     *
     * @param finalValue true 表示调用方是高炉/烟熏炉子类的 getBurnDuration，
     *                   其返回值不会再被 super 的 {@code /2} 处理（我们在子类 HEAD 直接拦下）。
     *                   此时指定燃料必须返回配方设定的完整时长；
     *                   通用燃料则要主动 {@code /2}，以保持原版「高炉/烟熏炉半速消耗燃料」的手感。
     *                   false 表示调用方是父类 AbstractFurnaceBlockEntity（即普通熔炉），按原值返回。
     * @return null 表示不接管，交回原版
     */
    public static Integer customBurnTime(AbstractFurnaceBlockEntity furnace, ItemStack fuel, boolean finalValue) {
        if (furnace == null || fuel == null || fuel.isEmpty() || furnace.getLevel() == null || furnace.getLevel().isClientSide()) return null;
        RecipeType<?> type = recipeType(furnace);
        ItemStack input = furnace.getItem(0);

        // 必须遍历完所有匹配当前输入的配方，命中一条可用的就返回，不能在第一条上直接 return。
        // 反例：同一个输入存在配方 A 与 B，A 只勾了熔炉、B 勾了高炉。
        // 若在 A 上直接 return，B 根本没机会被检查，高炉就永远点不着。
        boolean matchedAnyInput = false;
        boolean allowedHere = false;
        Integer resolved = null;

        for (SmeltingRecipeData data : SmeltingRecipeRegistry.list(furnace.getLevel().registryAccess())) {
            if (!matchesInput(input, data.input)) continue;
            matchedAnyInput = true;

            // 炉型开关：高炉看 blastAllowed，烟熏炉看 smokerAllowed，普通熔炉不受这两个开关约束。
            if (type == RecipeType.BLASTING && !data.blastAllowed) continue;
            if (type == RecipeType.SMOKING && !data.smokerAllowed) continue;
            allowedHere = true;

            int vanillaBurn = fuel.getBurnTime(type);
            if (data.genericFuelAllowed && vanillaBurn > 0) {
                // 通用燃料保持原版手感：高炉/烟熏炉半速消耗。
                return finalValue ? Math.max(1, vanillaBurn / 2) : vanillaBurn;
            }
            if (!data.fuel.isEmpty() && ItemStack.isSameItemSameComponents(data.fuel, fuel)) {
                // 指定燃料按配方设定的时长精确生效，不受高炉/烟熏炉的 /2 影响。
                // 关键点：必须 >=1，否则 0 会被原版当成「不是燃料」而不点火。
                return Math.max(1, Math.round(data.cookTime));
            }
            // 本条允许但燃料不匹配：记下「有允许的配方但这个燃料不行」，继续看下一条。
            resolved = 0;
        }

        // 输入没命中任何自定义配方：完全交回原版处理。
        if (!matchedAnyInput) return null;
        // 输入命中了自定义配方，但没有一条允许当前炉型：交回原版，
        // 避免把原版自带的同输入配方（例如原版铁矿在高炉里的冶炼）一起掐死。
        if (!allowedHere) return null;
        return resolved;
    }

    private static boolean matchesInput(ItemStack actual, ItemStack expected) {
        return actual != null && expected != null && !actual.isEmpty() && !expected.isEmpty()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static RecipeType<?> recipeType(AbstractFurnaceBlockEntity furnace) {
        if (furnace instanceof FurnaceTypeAccess access) return access.cnpcplus$getRecipeType();
        return RecipeType.SMELTING;
    }

    public interface FurnaceTypeAccess {
        RecipeType<?> cnpcplus$getRecipeType();
    }
}
