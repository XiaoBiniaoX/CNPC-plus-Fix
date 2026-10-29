package top.cnpcplus.smithing;

import net.minecraft.resources.ResourceLocation;

/**
 * 锻造配方解析器：把「源数据 SmithingRecipeData」解析成 Minecraft 能用的 CustomSmithingRecipe。
 * 227 从 SmeltingRecipeParser 简化复制：锻造台只有一种配方形态（1 源数据 → 1 recipe），
 * 不像熔炼按高炉/烟熏炉拆 1~3 个。
 *
 * <p>注册条件（228 放宽）：结果槽必须放置 + 三输入槽至少满足一个即可注册；
 * 空槽的槽位模式由 CustomSmithingRecipe.matches 严格判定（229：配方空→台必须空）。
 */
public final class SmithingRecipeParser {

    private SmithingRecipeParser() {}

    /** 结果非空且至少一个输入非空才成配方；返回 null 表示该源数据不可注册（由调用方跳过）。 */
    public static CustomSmithingRecipe parse(SmithingRecipeData data) {
        if (data == null) return null;
        if (data.result == null || data.result.isEmpty()) return null;
        boolean anyInput = (data.template != null && !data.template.isEmpty())
                || (data.base != null && !data.base.isEmpty())
                || (data.addition != null && !data.addition.isEmpty());
        if (!anyInput) return null;
        return new CustomSmithingRecipe(
                new ResourceLocation("cnpcplus", "smithing/" + data.id),
                data.template == null ? net.minecraft.world.item.ItemStack.EMPTY : data.template.copy(),
                data.base == null ? net.minecraft.world.item.ItemStack.EMPTY : data.base.copy(),
                data.addition == null ? net.minecraft.world.item.ItemStack.EMPTY : data.addition.copy(),
                data.result.copy(),
                data.nameOnly);
    }
}
