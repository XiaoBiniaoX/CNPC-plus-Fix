package bin.cnpcplus.craftingview;

import bin.cnpcplus.recipe.CraftUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecipePanel {
    public static final int PANEL_WIDTH = 110;
    public static final int VISIBLE_PER_PAGE = 10;

    private final boolean anvil;
    private final List<RecipeView> all = new ArrayList<RecipeView>();
    private final List<RecipeView> filtered = new ArrayList<RecipeView>();
    private int scrollOffset;
    private int selectedIndex = -1;
    private boolean collapsed;
    public final GuiTextField searchField;

    public RecipePanel(boolean anvil) {
        this.anvil = anvil;
        Minecraft mc = Minecraft.getMinecraft();
        this.searchField = new GuiTextField(0, mc.fontRenderer, 0, 0, 100, 12);
        this.searchField.setMaxStringLength(64);
        this.searchField.setEnableBackgroundDrawing(true);
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
        String q = searchField.getText();
        if (q == null) q = "";
        q = q.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (int i = 0; i < all.size(); i++) {
            RecipeView r = all.get(i);
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
        if (out != null && !out.isEmpty()) {
            String dn = out.getDisplayName();
            if (dn != null && dn.toLowerCase(Locale.ROOT).contains(q)) return true;
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return false;
        InventoryPlayer inv = mc.player.inventory;
        RecipeCarpentry recipe = anvil ? RecipeAccess.getAnvil(view.id) : RecipeAccess.getGlobal(view.id);
        if (recipe == null) return false;
        NonNullList<Ingredient> ings = recipe.getIngredients();
        if (ings == null) return false;
        for (int i = 0; i < ings.size(); i++) {
            Ingredient ing = ings.get(i);
            if (ing == null) continue;
            ItemStack[] arr = ing.getMatchingStacks();
            if (arr == null || arr.length == 0) continue;
            ItemStack required = arr[0];
            boolean found = false;
            for (int s = 0; s < inv.getSizeInventory(); s++) {
                ItemStack stack = inv.getStackInSlot(s);
                if (stack == null || stack.isEmpty()) continue;
                if (CraftUtils.matches(stack, required, recipe.ignoreDamage, recipe.ignoreNBT)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
