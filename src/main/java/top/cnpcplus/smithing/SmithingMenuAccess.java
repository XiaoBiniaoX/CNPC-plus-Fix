package top.cnpcplus.smithing;

/** 两端配方更新时刷新已经打开的锻造台，不关闭菜单、不丢输入物品。 */
public interface SmithingMenuAccess {
    void cnpcplus$refreshRecipes();
}
