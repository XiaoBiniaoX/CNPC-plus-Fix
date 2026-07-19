package bin.cnpcplus.craftingview;

import bin.cnpcplus.recipe.CraftUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipePanel {
    public static final int PANEL_WIDTH = 110;
    public static final int VISIBLE_PER_PAGE = 10;

    private final boolean anvil;
    private final List<RecipeView> all = new ArrayList<>();
    private final List<RecipeView> filtered = new ArrayList<>();
    private int scrollOffset;
    private int selectedIndex = -1;
    private boolean collapsed;
    public final EditBox searchField;

    public RecipePanel(boolean anvil) {
        this.anvil = anvil;
        this.searchField = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 12, Component.empty());
        this.searchField.setBordered(true);
        this.searchField.setMaxLength(64);
        this.searchField.setResponder(s -> rebuildFiltered());
        reload();
    }

    public boolean isAnvil() {
        return anvil;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
    }

    public void reload() {
        all.clear();
        all.addAll(anvil ? RecipeAccess.getAnvilRecipes() : RecipeAccess.getGlobalRecipes());
        rebuildFiltered();
    }

    public void rebuildFiltered() {
        String q = searchField.getValue();
        if (q == null) q = "";
        q = q.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (RecipeView r : all) {
            if (q.isEmpty() || matchSearch(r, q)) {
                filtered.add(r);
            }
        }
        scrollOffset = Math.min(scrollOffset, Math.max(0, filtered.size() - VISIBLE_PER_PAGE));
        if (selectedIndex >= filtered.size()) selectedIndex = -1;
    }

    private boolean matchSearch(RecipeView r, String q) {
        if (r.name != null && r.name.toLowerCase(Locale.ROOT).contains(q)) return true;
        ItemStack out = r.getRecipeOutput();
        if (out != null && !out.isEmpty() && out.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        return false;
    }

    public void scroll(int delta) {
        int max = Math.max(0, filtered.size() - VISIBLE_PER_PAGE);
        scrollOffset = Math.max(0, Math.min(max, scrollOffset + delta));
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getFilteredSize() {
        return filtered.size();
    }

    public int getVisiblePerPage() {
        return VISIBLE_PER_PAGE;
    }

    public List<RecipeView> getVisible() {
        int from = Math.min(scrollOffset, filtered.size());
        int to = Math.min(from + VISIBLE_PER_PAGE, filtered.size());
        return filtered.subList(from, to);
    }

    public void selectRecipe(RecipeView recipe) {
        if (recipe == null) {
            selectedIndex = -1;
            return;
        }
        selectedIndex = filtered.indexOf(recipe);
    }

    public RecipeView getSelectedRecipe() {
        if (selectedIndex < 0 || selectedIndex >= filtered.size()) return null;
        return filtered.get(selectedIndex);
    }

    public int getSelectedVisibleIndex() {
        if (selectedIndex < 0) return -1;
        int vis = selectedIndex - scrollOffset;
        if (vis < 0 || vis >= VISIBLE_PER_PAGE) return -1;
        return vis;
    }

    public int getPanelX(int guiLeft) {
        return guiLeft - PANEL_WIDTH - 4;
    }

    public boolean isCraftable(RecipeView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Inventory inv = mc.player.getInventory();
        RecipeCarpentry recipe = anvil ? RecipeAccess.getAnvil(view.id) : RecipeAccess.getGlobal(view.id);
        if (recipe == null) return false;
        var ings = recipe.getIngredients();
        if (ings == null) return false;
        // simple: every non-empty ingredient has at least one matching item in inv
        for (Ingredient ing : ings) {
            if (ing == null || ing.isEmpty()) continue;
            ItemStack[] arr = ing.getItems();
            if (arr.length == 0) continue;
            ItemStack required = arr[0];
            boolean found = false;
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.items.get(i);
                if (s.isEmpty()) continue;
                if (CraftUtils.matches(s, required, recipe.ignoreDamage, recipe.ignoreNBT)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}