package bin.cnpcplus.smelting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** 自定义配方燃料规则；没有命中自定义配方时返回 null，交给原版。 */
public final class SmeltingFuelRules {
    private SmeltingFuelRules() {}

    public static Integer customBurnTime(AbstractFurnaceBlockEntity furnace, ItemStack fuel) {
        if (furnace == null || fuel == null || fuel.isEmpty() || furnace.getLevel() == null || furnace.getLevel().isClientSide()) return null;
        RecipeType<?> type = recipeType(furnace);
        for (SmeltingRecipeData data : SmeltingRecipeRegistry.list(furnace.getLevel().registryAccess())) {
            if (!matchesInput(furnace.getItem(0), data.input)) continue;
            if (type == RecipeType.BLASTING && !data.blastAllowed) continue;
            if (type == RecipeType.SMOKING && !data.smokerAllowed) continue;
            int vanillaBurn = fuel.getBurnTime(type);
            boolean generic = data.genericFuelAllowed && vanillaBurn > 0;
            if (generic) return vanillaBurn;
            if (!data.fuel.isEmpty() && ItemStack.isSameItemSameComponents(data.fuel, fuel)) {
                return Math.max(1, Math.round(data.cookTime));
            }
            return 0;
        }
        return null;
    }

    private static boolean matchesInput(ItemStack actual, ItemStack expected) {
        return actual != null && expected != null && !actual.isEmpty() && !expected.isEmpty()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    @SuppressWarnings("unchecked")
    private static RecipeType<?> recipeType(AbstractFurnaceBlockEntity furnace) {
        if (furnace instanceof FurnaceTypeAccess access) return access.cnpcplus$getRecipeType();
        return RecipeType.SMELTING;
    }

    public interface FurnaceTypeAccess {
        RecipeType<?> cnpcplus$getRecipeType();
    }
}
