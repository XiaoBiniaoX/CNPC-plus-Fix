package bin.cnpcplus.craftingview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;

import java.util.List;

public final class RecipePanelRenderer {

    private static final int PADDING = 4;
    private static final int COLLAPSE_BTN_W = 12;
    private static final int SEARCH_FIELD_H = 12;
    private static final int RECIPE_ROW_H = 16;
    private static final int GRID_CELL = 16;

    private static final int COLOR_BG = 0xCC2D2D2D;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_ROW_SEL = 0x88AAAAFF;
    private static final int COLOR_ROW_HOV = 0x44FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_PLUS_BTN = 0xFF336633;
    private static final int COLOR_PLUS_HOV = 0xFF44AA44;
    private static final int COLOR_CRAFTABLE = 0xFF33DD33;
    private static final int COLOR_OVERLAY_BG = 0xF0202020;

    private static final int HEADER_HEIGHT = PADDING + 10 + SEARCH_FIELD_H + 3 + 1 + 3 + 7;

    private RecipePanelRenderer() {}

    public static void render(GuiScreen screen, RecipePanel panel, int mouseX, int mouseY, int guiLeft, int guiTop) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        int px = panel.getPanelX(guiLeft);
        int py = guiTop;

        if (panel.isCollapsed()) {
            drawCollapsedTab(guiLeft - COLLAPSE_BTN_W - 8, py, mouseX, mouseY, font);
            return;
        }

        int pw = RecipePanel.PANEL_WIDTH;
        List<RecipeView> visible = panel.getVisible();
        RecipeView sel = panel.getSelectedRecipe();

        int ph = calcPanelHeight(panel);
        fill(px, py, px + pw, py + ph, COLOR_BG);
        drawBorder(px, py, pw, ph);

        int cx = px + PADDING;
        int cy = py + PADDING;

        drawCollapseButton(px + pw - COLLAPSE_BTN_W - 2, py + 2, mouseX, mouseY, font);
        cy = drawHeader(cx, px, cy, pw, panel, mouseX, mouseY, font);

        ItemStack tooltipStack = null;
        int listTop = cy;
        for (int i = 0; i < visible.size(); i++) {
            RecipeView recipe = visible.get(i);
            boolean selected = recipe.equals(sel);
            ItemStack rowTip = drawRecipeRow(cx, px, cy, pw, recipe, selected, mouseX, mouseY, font, mc, panel);
            if (rowTip != null) tooltipStack = rowTip;
            cy += RECIPE_ROW_H;
        }

        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) {
            font.drawString("\u25BC", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }

        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx >= 0 && sel != null) {
            int rowY = listTop + selIdx * RECIPE_ROW_H;
            int gridSize = panel.isAnvil() ? 4 : 3;
            ItemStack overlayTip = drawIngredientOverlay(px, pw, rowY, sel, gridSize, mouseX, mouseY, font, screen.height, mc);
            int oh = PADDING + 10 + gridSize * (GRID_CELL + 1) + PADDING;
            int oy = overlayY(rowY, screen.height, oh);
            boolean overOverlay = mouseX >= px && mouseX < px + pw && mouseY >= oy && mouseY < oy + oh;
            if (overOverlay) tooltipStack = overlayTip;
        }

        if (tooltipStack != null && !tooltipStack.isEmpty()) {
            // tooltip optional; skip protected GuiScreen.renderToolTip
        }
    }

    private static int drawHeader(int cx, int px, int cy, int pw, RecipePanel panel,
                                  int mouseX, int mouseY, FontRenderer font) {
        String titleKey = panel.isAnvil() ? "cnpcplus.craftingview.title.anvil" : "cnpcplus.craftingview.title.workbench";
        font.drawString(I18n.translateToLocal(titleKey), cx, cy, COLOR_TEXT);
        cy += 10;

        panel.searchField.x = cx;
        panel.searchField.y = cy;
        panel.searchField.width = pw - PADDING * 2;
        panel.searchField.drawTextBox();
        cy += SEARCH_FIELD_H + 3;

        fill(px, cy, px + pw, cy + 1, COLOR_BORDER);
        cy += 1 + 3;

        if (panel.getScrollOffset() > 0) {
            font.drawString("\u25B2", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }
        cy += 7;
        return cy;
    }

    private static ItemStack drawRecipeRow(int cx, int px, int ry, int pw, RecipeView recipe,
                                           boolean selected, int mouseX, int mouseY, FontRenderer font, Minecraft mc,
                                           RecipePanel panel) {
        boolean hovered = mouseX >= cx && mouseX < px + pw - PADDING && mouseY >= ry && mouseY < ry + RECIPE_ROW_H;
        if (selected) fill(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_SEL);
        else if (hovered) fill(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_HOV);

        ItemStack result = recipe.getRecipeOutput();
        ItemStack tooltipStack = null;
        if (result != null && !result.isEmpty()) {
            drawItem(mc, result, cx, ry);
            if (mouseX >= cx && mouseX < cx + GRID_CELL && mouseY >= ry && mouseY < ry + GRID_CELL) {
                tooltipStack = result;
            }
        }

        String displayName = result != null && !result.isEmpty()
                ? result.getDisplayName()
                : (recipe.name != null ? recipe.name : "");
        font.drawString(font.trimStringToWidth(displayName, pw - PADDING * 2 - 18 - 14), cx + 18, ry + 4, COLOR_TEXT);

        int btnX = px + pw - PADDING - 12;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + 10 && mouseY >= ry + 3 && mouseY < ry + 13;
        fill(btnX, ry + 3, btnX + 10, ry + 13, btnHov ? COLOR_PLUS_HOV : COLOR_PLUS_BTN);
        font.drawString("+", btnX + 2, ry + 4, COLOR_TEXT);

        if (panel.isCraftable(recipe)) {
            int greenX = btnX + 11;
            fill(greenX, ry + 4, greenX + 2, ry + 12, COLOR_CRAFTABLE);
        }

        return tooltipStack;
    }

    private static ItemStack drawIngredientOverlay(int px, int pw, int rowY, RecipeView recipe,
                                                   int gridSize, int mouseX, int mouseY, FontRenderer font, int screenHeight,
                                                   Minecraft mc) {
        int oh = PADDING + 10 + gridSize * (GRID_CELL + 1) + PADDING;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 200);

        int oy = overlayY(rowY, screenHeight, oh);
        int ox = px;
        fill(ox, oy, ox + pw, oy + oh, COLOR_OVERLAY_BG);
        drawBorder(ox, oy, pw, oh);

        int cx = ox + PADDING;
        int cy = oy + PADDING;
        font.drawString(I18n.translateToLocal("cnpcplus.craftingview.preview"), cx, cy, COLOR_TEXT_DIM);
        cy += 10;

        ItemStack tooltipStack = null;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int gx = cx + col * (GRID_CELL + 1);
                int gy = cy + row * (GRID_CELL + 1);
                fill(gx, gy, gx + GRID_CELL, gy + GRID_CELL, 0xFF333333);
                int recipeRow = row - recipe.offsetY;
                int recipeCol = col - recipe.offsetX;
                ItemStack ing = (recipeRow >= 0 && recipeCol >= 0
                        && recipeRow < recipe.recipeHeight && recipeCol < recipe.recipeWidth)
                        ? recipe.getCraftingItem(recipeRow * recipe.recipeWidth + recipeCol) : ItemStack.EMPTY;
                if (ing != null && !ing.isEmpty()) {
                    drawItem(mc, ing, gx, gy);
                    if (mouseX >= gx && mouseX < gx + GRID_CELL && mouseY >= gy && mouseY < gy + GRID_CELL) {
                        tooltipStack = ing;
                    }
                }
            }
        }
        GlStateManager.popMatrix();
        return tooltipStack;
    }

    private static void drawItem(Minecraft mc, ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, stack, x, y, null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
    }

    private static int calcPanelHeight(RecipePanel panel) {
        int h = HEADER_HEIGHT;
        h += panel.getVisible().size() * RECIPE_ROW_H;
        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) h += 10;
        return h + PADDING;
    }

    private static void drawCollapsedTab(int x, int y, int mouseX, int mouseY, FontRenderer font) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W + 4 && mouseY >= y && mouseY < y + 20;
        fill(x, y, x + COLLAPSE_BTN_W + 4, y + 20, hov ? 0xCC444444 : COLOR_BG);
        drawBorder(x, y, COLLAPSE_BTN_W + 4, 20);
        font.drawString(">", x + 3, y + 6, COLOR_TEXT);
    }

    private static void drawCollapseButton(int x, int y, int mouseX, int mouseY, FontRenderer font) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W && mouseY >= y && mouseY < y + 12;
        fill(x, y, x + COLLAPSE_BTN_W, y + 12, hov ? 0xCC555555 : 0xCC333333);
        font.drawString("<", x + 2, y + 2, COLOR_TEXT);
    }

    private static void drawBorder(int x, int y, int w, int h) {
        fill(x, y, x + w, y + 1, COLOR_BORDER);
        fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        fill(x, y, x + 1, y + h, COLOR_BORDER);
        fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }

    private static void fill(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    private static int overlayY(int rowY, int screenHeight, int overlayH) {
        int oy = rowY + RECIPE_ROW_H;
        if (oy + overlayH > screenHeight) oy = rowY - overlayH;
        if (oy < 0) oy = 0;
        return oy;
    }

    public static boolean isCollapseButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - COLLAPSE_BTN_W - 8;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        }
        int px = panel.getPanelX(guiLeft);
        int x = px + RecipePanel.PANEL_WIDTH - COLLAPSE_BTN_W - 2;
        int y = guiTop + 2;
        return mx >= x && mx < x + COLLAPSE_BTN_W && my >= y && my < y + 12;
    }

    public static int getRecipeRowHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        int px = panel.getPanelX(guiLeft);
        int cx = px + PADDING;
        int visibleCount = panel.getVisible().size();
        for (int i = 0; i < visibleCount; i++) {
            int ry = guiTop + HEADER_HEIGHT + i * RECIPE_ROW_H;
            if (mx >= cx && mx < px + RecipePanel.PANEL_WIDTH - PADDING && my >= ry && my < ry + RECIPE_ROW_H) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isPlusButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my, int rowIndex) {
        int px = panel.getPanelX(guiLeft);
        int ry = guiTop + HEADER_HEIGHT + rowIndex * RECIPE_ROW_H;
        int btnX = px + RecipePanel.PANEL_WIDTH - PADDING - 12;
        return mx >= btnX && mx < btnX + 10 && my >= ry + 3 && my < ry + 13;
    }

    public static boolean isOverlayHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) return false;
        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx < 0) return false;
        RecipeView sel = panel.getSelectedRecipe();
        if (sel == null) return false;
        int px = panel.getPanelX(guiLeft);
        int rowY = guiTop + HEADER_HEIGHT + selIdx * RECIPE_ROW_H;
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        int screenHeight = screen != null ? screen.height : 240;
        int gridSize = panel.isAnvil() ? 4 : 3;
        int oh = PADDING + 10 + gridSize * (GRID_CELL + 1) + PADDING;
        int oy = overlayY(rowY, screenHeight, oh);
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= oy && my < oy + oh;
    }

    public static boolean isPanelHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - 4 - COLLAPSE_BTN_W;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        }
        int px = panel.getPanelX(guiLeft);
        int ph = calcPanelHeight(panel);
        int maxH = ph;
        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx >= 0) {
            RecipeView sel = panel.getSelectedRecipe();
            if (sel != null) {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                int screenHeight = screen != null ? screen.height : 240;
                int gridSize = panel.isAnvil() ? 4 : 3;
                int oh = PADDING + 10 + gridSize * (GRID_CELL + 1) + PADDING;
                int rowY = guiTop + HEADER_HEIGHT + selIdx * RECIPE_ROW_H;
                int oy = overlayY(rowY, screenHeight, oh);
                maxH = Math.max(maxH, oy + oh - guiTop);
            }
        }
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= guiTop && my < guiTop + maxH;
    }
}
