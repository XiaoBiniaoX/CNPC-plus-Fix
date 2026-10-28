package bin.cnpcplus.common;

import bin.cnpcplus.crafting.CustomRecipe;

public interface IAnvilRecipeAccess {
    boolean cnpcplus$isCustomRecipe();
    void cnpcplus$setTakingRecipe(boolean taking);
    /** 当前匹配到的自定义铁砧配方；结果槽据此按配方数量扣材料。 */
    CustomRecipe cnpcplus$customRecipe();
}
