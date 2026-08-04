package top.cnpcplus.craftingview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RecipePanel {

    public static final int PANEL_WIDTH = 124;
    public static final int RECIPES_PER_PAGE = 7;

    private final boolean isAnvil;
    private final List<RecipeView> allRecipes = new ArrayList<>();
    private final List<RecipeView> filtered = new ArrayList<>();
    private final List<RecipeView> cachedVisible = new ArrayList<>();

    private boolean collapsed = false;
    private int scrollOffset = 0;
    private RecipeView selectedRecipe = null;

    public EditBox searchField;

    public RecipePanel(boolean isAnvil) {
        this.isAnvil = isAnvil;
        var font = Minecraft.getInstance().font;
        /* 搜索框 */
        searchField = new EditBox(font, 0, 0, PANEL_WIDTH - 8, 12, Component.empty());
        searchField.setMaxLength(32);
        searchField.setValue("");
        /* 预载当前容器的全部配方 */
        refreshRecipes();
        searchField.setResponder(s -> rebuildFiltered());
    }

    /** 从当前容器对应的 map 加载全部配方 */
    public void refreshRecipes() {
        allRecipes.clear();
        for (RecipeView r : RecipeAccess.getRecipes(!isAnvil)) {
            allRecipes.add(r);
        }
        rebuildFiltered();
    }

    /** 按搜索词过滤 */
    public void rebuildFiltered() {
        String query = searchField.getValue().toLowerCase().trim();

        filtered.clear();
        for (RecipeView recipe : allRecipes) {
            if (!query.isEmpty() && !matchesSearch(recipe, query)) continue;
            filtered.add(recipe);
        }

        int maxScroll = Math.max(0, filtered.size() - RECIPES_PER_PAGE);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        updateVisibleCache();
    }

    /** 搜索匹配（配方名 + 输出物品名） */
    private boolean matchesSearch(RecipeView recipe, String query) {
        String lowerName = recipe.getLowerCaseName();
        if (lowerName != null && lowerName.contains(query)) return true;
        String lowerDisplayName = recipe.getLowerCaseDisplayName();
        if (lowerDisplayName != null && lowerDisplayName.contains(query)) return true;
        return false;
    }

    /* ==================== 可合成判断（供渲染器使用） ==================== */

    /**
     * 检查玩家背包是否有足够材料合成该配方
     * 匹配逻辑与 CraftUtils.matches() 一致（同时使用配方标记 + 全局模糊规则）
     */
    public boolean isCraftable(RecipeView recipe) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        Inventory inv = player.getInventory();

        int w = recipe.recipeWidth;
        int h = recipe.recipeHeight;
        boolean[] used = new boolean[inv.items.size()];

        for (int i = 0; i < w * h; i++) {
            ItemStack required = recipe.getCraftingItem(i);
            if (required == null || required.isEmpty()) continue;

            boolean found = false;
            for (int j = 0; j < inv.items.size(); j++) {
                if (used[j]) continue;
                if (CraftUtils.matches(inv.items.get(j), required,
                        recipe.ignoreDamage, recipe.ignoreNBT)) {
                    used[j] = true;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /* ==================== 视图缓存 ==================== */

    private void updateVisibleCache() {
        cachedVisible.clear();
        int end = Math.min(scrollOffset + RECIPES_PER_PAGE, filtered.size());
        for (int i = scrollOffset; i < end; i++) {
            cachedVisible.add(filtered.get(i));
        }
    }

    public List<RecipeView> getVisible() { return cachedVisible; }
    public int getScrollOffset() { return scrollOffset; }
    public int getVisiblePerPage() { return RECIPES_PER_PAGE; }
    public int getFilteredSize() { return filtered.size(); }

    public void scroll(int delta) {
        int maxScroll = Math.max(0, filtered.size() - RECIPES_PER_PAGE);
        scrollOffset = Math.max(0, Math.min(scrollOffset + delta, maxScroll));
        updateVisibleCache();
    }

    public void selectRecipe(RecipeView recipe) { selectedRecipe = recipe; }

    public int getSelectedVisibleIndex() {
        if (selectedRecipe == null) return -1;
        int idx = filtered.indexOf(selectedRecipe);
        if (idx < scrollOffset || idx >= scrollOffset + RECIPES_PER_PAGE) return -1;
        return idx - scrollOffset;
    }

    public void toggleCollapsed() { collapsed = !collapsed; }
    public boolean isCollapsed() { return collapsed; }
    public RecipeView getSelectedRecipe() { return selectedRecipe; }
    public boolean isAnvil() { return isAnvil; }
    public int getPanelX(int guiLeft) { return guiLeft - PANEL_WIDTH - 4; }
}
