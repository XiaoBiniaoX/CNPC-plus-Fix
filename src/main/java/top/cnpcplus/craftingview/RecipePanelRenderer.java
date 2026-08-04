package top.cnpcplus.craftingview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 渲染合成面板及其覆盖层（材料格子预览）
 */
public class RecipePanelRenderer {

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
    /** 可合成指示器颜色 */
    private static final int COLOR_CRAFTABLE = 0xFF33DD33;
    private static final int COLOR_OVERLAY_BG = 0xF0202020;

    /* 没有分类标签，表头只需要标题 + 搜索框 + 分隔线 */
    private static final int HEADER_HEIGHT = PADDING + 10 + SEARCH_FIELD_H + 3 + 1 + 3 + 7;

    /* ==================== 主渲染入口 ==================== */

    public static void render(GuiGraphics gr, Screen screen, RecipePanel panel, int mouseX, int mouseY, int guiLeft, int guiTop) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int px = panel.getPanelX(guiLeft);
        int py = guiTop;

        if (panel.isCollapsed()) {
            drawCollapsedTab(gr, guiLeft - COLLAPSE_BTN_W - 8, py, mouseX, mouseY, font);
            return;
        }

        int pw = RecipePanel.PANEL_WIDTH;
        List<RecipeView> visible = panel.getVisible();
        RecipeView sel = panel.getSelectedRecipe();

        int ph = calcPanelHeight(panel);
        fill(gr, px, py, px + pw, py + ph, COLOR_BG);
        drawBorder(gr, px, py, pw, ph);

        int cx = px + PADDING;
        int cy = py + PADDING;

        drawCollapseButton(gr, px + pw - COLLAPSE_BTN_W - 2, py + 2, mouseX, mouseY, font);
        cy = drawHeader(gr, cx, px, cy, pw, panel, mouseX, mouseY, font);

        ItemStack tooltipStack = null;
        int listTop = cy;
        for (int i = 0; i < visible.size(); i++) {
            RecipeView recipe = visible.get(i);
            boolean selected = recipe.equals(sel);
            ItemStack rowTip = drawRecipeRow(gr, cx, px, cy, pw, recipe, selected, mouseX, mouseY, font, mc, panel);
            if (rowTip != null) tooltipStack = rowTip;
            cy += RECIPE_ROW_H;
        }

        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) {
            gr.drawString(font, "\u25BC", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }

        /* ----- 选中配方的材料覆盖层 ----- */
        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx >= 0 && sel != null) {
            int rowY = listTop + selIdx * RECIPE_ROW_H;
            int gridSize = panel.isAnvil() ? 4 : 3;
            ItemStack overlayTip = drawIngredientOverlay(gr, px, pw, rowY, sel, gridSize, mouseX, mouseY, font, screen.height, mc);
            int oh = PADDING + 10 + gridSize * (GRID_CELL + 1) + PADDING;
            int oy = overlayY(rowY, screen.height, oh);
            boolean overOverlay = mouseX >= px && mouseX < px + pw && mouseY >= oy && mouseY < oy + oh;
            if (overOverlay) tooltipStack = overlayTip;
        }

        if (tooltipStack != null) {
            gr.renderTooltip(font, tooltipStack, mouseX, mouseY);
        }
    }

    /* ==================== 表头（标题 + 搜索框） ==================== */

    private static int drawHeader(GuiGraphics gr, int cx, int px, int cy, int pw, RecipePanel panel,
                                   int mouseX, int mouseY, Font font) {
        /* 标题：工作台 / 木工台 */
        gr.drawString(font, Component.translatable(panel.isAnvil() ? "cnpcplus.crafting.anvil" : "cnpcplus.crafting.carpentry"), cx, cy, COLOR_TEXT);
        cy += 10;

        /* 搜索框 */
        panel.searchField.setX(cx);
        panel.searchField.setY(cy);
        panel.searchField.setWidth(pw - PADDING * 2);
        panel.searchField.render(gr, mouseX, mouseY, 0);
        cy += SEARCH_FIELD_H + 3;

        /* 分隔线 */
        fill(gr, px, cy, px + pw, cy + 1, COLOR_BORDER);
        cy += 1 + 3;

        /* 上箭头（可滚动时显示） */
        if (panel.getScrollOffset() > 0) {
            gr.drawString(font, "\u25B2", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }
        cy += 7;

        return cy;
    }

    /* ==================== 每个配方行 ==================== */

    private static ItemStack drawRecipeRow(GuiGraphics gr, int cx, int px, int ry, int pw, RecipeView recipe,
                                            boolean selected, int mouseX, int mouseY, Font font, Minecraft mc,
                                            RecipePanel panel) {
        boolean hovered = mouseX >= cx && mouseX < px + pw - PADDING && mouseY >= ry && mouseY < ry + RECIPE_ROW_H;
        if (selected) fill(gr, cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_SEL);
        else if (hovered) fill(gr, cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_HOV);

        ItemStack result = recipe.getRecipeOutput();
        ItemStack tooltipStack = null;
        if (result != null && !result.isEmpty()) {
            gr.renderItem(result, cx, ry);
            if (mouseX >= cx && mouseX < cx + GRID_CELL && mouseY >= ry && mouseY < ry + GRID_CELL) {
                tooltipStack = result;
            }
        }

        /* 输出物品名 */
        String displayName = result != null && !result.isEmpty()
                ? result.getHoverName().getString()
                : (recipe.name != null ? recipe.name : "");
        gr.drawString(font, font.plainSubstrByWidth(displayName, pw - PADDING * 2 - 18 - 14), cx + 18, ry + 4, COLOR_TEXT);

        /* "+" 按钮 */
        int btnX = px + pw - PADDING - 12;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + 10 && mouseY >= ry + 3 && mouseY < ry + 13;
        fill(gr, btnX, ry + 3, btnX + 10, ry + 13, btnHov ? COLOR_PLUS_HOV : COLOR_PLUS_BTN);
        gr.drawString(font, "+", btnX + 2, ry + 4, COLOR_TEXT);

        /* 可合成指示器：在 "+" 右边画一条绿色竖线 */
        if (panel.isCraftable(recipe)) {
            int greenX = btnX + 11;   // 紧贴 "+" 右侧（1px 间距）
            fill(gr, greenX, ry + 4, greenX + 2, ry + 12, COLOR_CRAFTABLE);
        }

        return tooltipStack;
    }

    /* ==================== 材料覆盖层 ==================== */

    private static ItemStack drawIngredientOverlay(GuiGraphics gr, int px, int pw, int rowY, RecipeView recipe,
                                                    int gridSize, int mouseX, int mouseY, Font font, int screenHeight,
                                                    Minecraft mc) {
        int oh = PADDING + 10 + gridSize * (GRID_CELL + 1) + PADDING;
        gr.pose().pushPose();
        gr.pose().translate(0, 0, 200);

        int oy = overlayY(rowY, screenHeight, oh);
        int ox = px;
        fill(gr, ox, oy, ox + pw, oy + oh, COLOR_OVERLAY_BG);
        drawBorder(gr, ox, oy, pw, oh);

        int cx = ox + PADDING;
        int cy = oy + PADDING;
        gr.drawString(font, Component.translatable("cnpcplus.crafting.recipe"), cx, cy, COLOR_TEXT_DIM);
        cy += 10;

        ItemStack tooltipStack = null;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int gx = cx + col * (GRID_CELL + 1);
                int gy = cy + row * (GRID_CELL + 1);
                fill(gr, gx, gy, gx + GRID_CELL, gy + GRID_CELL, 0xFF333333);
                int recipeRow = row - recipe.offsetY;
                int recipeCol = col - recipe.offsetX;
                ItemStack ing = (recipeRow >= 0 && recipeCol >= 0 && recipeRow < recipe.recipeHeight && recipeCol < recipe.recipeWidth)
                        ? recipe.getCraftingItem(recipeRow * recipe.recipeWidth + recipeCol) : null;
                if (ing != null && !ing.isEmpty()) {
                    gr.renderItem(ing, gx, gy);
                    if (mouseX >= gx && mouseX < gx + GRID_CELL && mouseY >= gy && mouseY < gy + GRID_CELL) {
                        tooltipStack = ing;
                    }
                }
            }
        }
        gr.pose().popPose();

        return tooltipStack;
    }

    /* ==================== 格子尺寸 ==================== */

    /** 根据配方宽高决定覆盖层格子维度（>3→4×4，否则 3×3） */
    public static int calcGridSize(RecipeView recipe) {
        return (recipe.recipeWidth + recipe.offsetX > 3 || recipe.recipeHeight + recipe.offsetY > 3) ? 4 : 3;
    }

    /* ==================== 面板高度 ==================== */

    private static int calcPanelHeight(RecipePanel panel) {
        /* 不含分类标签，直接用 HEADER_HEIGHT */
        int h = HEADER_HEIGHT;
        h += panel.getVisible().size() * RECIPE_ROW_H;
        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) h += 10;
        return h + PADDING;
    }

    /* ==================== 辅助绘制 ==================== */

    private static void drawCollapsedTab(GuiGraphics gr, int x, int y, int mouseX, int mouseY, Font font) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W + 4 && mouseY >= y && mouseY < y + 20;
        fill(gr, x, y, x + COLLAPSE_BTN_W + 4, y + 20, hov ? 0xCC444444 : COLOR_BG);
        drawBorder(gr, x, y, COLLAPSE_BTN_W + 4, 20);
        gr.drawString(font, ">", x + 3, y + 6, COLOR_TEXT);
    }

    private static void drawCollapseButton(GuiGraphics gr, int x, int y, int mouseX, int mouseY, Font font) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W && mouseY >= y && mouseY < y + 12;
        fill(gr, x, y, x + COLLAPSE_BTN_W, y + 12, hov ? 0xCC555555 : 0xCC333333);
        gr.drawString(font, "<", x + 2, y + 2, COLOR_TEXT);
    }

    private static void drawBorder(GuiGraphics gr, int x, int y, int w, int h) {
        fill(gr, x, y, x + w, y + 1, COLOR_BORDER);
        fill(gr, x, y + h - 1, x + w, y + h, COLOR_BORDER);
        fill(gr, x, y, x + 1, y + h, COLOR_BORDER);
        fill(gr, x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }

    private static void fill(GuiGraphics gr, int x1, int y1, int x2, int y2, int color) {
        gr.fill(x1, y1, x2, y2, color);
    }

    private static int overlayY(int rowY, int screenHeight, int overlayH) {
        int oy = rowY + RECIPE_ROW_H;
        if (oy + overlayH > screenHeight) oy = rowY - overlayH;
        if (oy < 0) oy = 0;
        return oy;
    }

    /* ==================== 点击判断 ==================== */

    public static boolean isCollapseButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - COLLAPSE_BTN_W - 8;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        } else {
            int px = panel.getPanelX(guiLeft);
            int x = px + RecipePanel.PANEL_WIDTH - COLLAPSE_BTN_W - 2;
            int y = guiTop + 2;
            return mx >= x && mx < x + COLLAPSE_BTN_W && my >= y && my < y + 12;
        }
    }

    public static int getRecipeRowHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        int px = panel.getPanelX(guiLeft);
        int cx = px + PADDING;
        int visibleCount = panel.getVisible().size();
        for (int i = 0; i < visibleCount; i++) {
            int ry = guiTop + HEADER_HEIGHT + i * RECIPE_ROW_H;
            if (mx >= cx && mx < px + RecipePanel.PANEL_WIDTH - PADDING && my >= ry && my < ry + RECIPE_ROW_H) return i;
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
        Screen screen = Minecraft.getInstance().screen;
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
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= guiTop && my < guiTop + ph;
    }
}
