package bin.cnpcplus.craftingview;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.craftingview.network.PacketFillCraftingGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.client.gui.player.GuiNpcCarpentryBench;

import java.util.List;

/**
 * Client-side crafting view sidebar for workbench + carpentry bench.
 */
@EventBusSubscriber(modid = bin.cnpcplus.CnpcPlus.MODID, value = Dist.CLIENT)
public final class CraftingViewEvents {
    private static RecipePanel activePanel;
    private static int reloadTicker;

    private CraftingViewEvents() {}

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        syncActivePanel(event.getNewScreen());
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        syncActivePanel(event.getScreen());
        if (activePanel == null) return;
        if ((++reloadTicker % 40) == 0) {
            activePanel.reload();
        }
        Screen screen = event.getScreen();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;
        // refresh list occasionally so newly saved recipes appear
        RecipePanelRenderer.render(event.getGuiGraphics(), screen, activePanel,
                (int) event.getMouseX(), (int) event.getMouseY(), gl, gt);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (activePanel == null) return;
        if ((++reloadTicker % 40) == 0) {
            activePanel.reload();
        }
        Screen screen = event.getScreen();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;
        int mx = (int) event.getMouseX();
        int my = (int) event.getMouseY();
        if (RecipePanelRenderer.isPanelHit(activePanel, gl, gt, mx, my)) {
            activePanel.scroll(event.getScrollDeltaY() > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (activePanel == null) return;
        if ((++reloadTicker % 40) == 0) {
            activePanel.reload();
        }
        Screen screen = event.getScreen();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;
        if (event.getButton() != 0) return;
        int mx = (int) event.getMouseX();
        int my = (int) event.getMouseY();

        if (!RecipePanelRenderer.isPanelHit(activePanel, gl, gt, mx, my)) {
            activePanel.searchField.setFocused(false);
            // click outside panel closes preview (deselect)
            if (activePanel.getSelectedRecipe() != null) {
                activePanel.selectRecipe(null);
            }
            return;
        }
        event.setCanceled(true);

        if (RecipePanelRenderer.isCollapseButtonHit(activePanel, gl, gt, mx, my)) {
            playClick();
            activePanel.toggleCollapsed();
            return;
        }
        if (activePanel.isCollapsed()) return;

        if (RecipePanelRenderer.isOverlayHit(activePanel, gl, gt, mx, my)) {
            // click on overlay background closes preview
            playClick();
            activePanel.selectRecipe(null);
            return;
        }

        activePanel.searchField.mouseClicked(mx, my, 0);

        int rowIdx = RecipePanelRenderer.getRecipeRowHit(activePanel, gl, gt, mx, my);
        if (rowIdx >= 0) {
            List<RecipeView> visible = activePanel.getVisible();
            if (rowIdx < visible.size()) {
                RecipeView recipe = visible.get(rowIdx);
                if (RecipePanelRenderer.isPlusButtonHit(activePanel, gl, gt, mx, my, rowIdx)) {
                    playClick();
                    PacketDistributor.sendToServer(new PacketFillCraftingGrid(recipe.id));
                } else if (recipe.equals(activePanel.getSelectedRecipe())) {
                    playClick();
                    activePanel.selectRecipe(null);
                } else {
                    playClick();
                    activePanel.selectRecipe(recipe);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (activePanel == null || activePanel.isCollapsed()) return;
        if (!activePanel.searchField.isFocused()) return;
        if (activePanel.searchField.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            activePanel.rebuildFiltered();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (activePanel == null || activePanel.isCollapsed()) return;
        if (!activePanel.searchField.isFocused()) return;
        if (activePanel.searchField.charTyped(event.getCodePoint(), event.getModifiers())) {
            activePanel.rebuildFiltered();
            event.setCanceled(true);
        }
    }

    /** 侧栏显示开关（热加载）：关闭立即隐藏，打开即时恢复；非目标界面清理面板 */
    private static void syncActivePanel(Screen screen) {
        if (!CnpcPlusConfig.CRAFTING_VIEW_ENABLED.get()) {
            activePanel = null;
            return;
        }
        boolean target = screen instanceof GuiNpcCarpentryBench || screen instanceof CraftingScreen;
        if (!target) {
            activePanel = null;
            return;
        }
        // ESC close does not fire ScreenEvent.Opening, so a stale panel from the other
        // container type could survive and show the wrong recipe list. Rebuild when
        // the container type (anvil vs workbench) does not match the current screen.
        boolean anvil = screen instanceof GuiNpcCarpentryBench;
        if (activePanel == null || activePanel.isAnvil() != anvil) {
            activePanel = new RecipePanel(anvil);
        }
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static int getGuiLeft(Screen screen) {
        // CNPC GuiBasicContainer uses public guiLeft
        try {
            var f = findField(screen.getClass(), "guiLeft");
            if (f != null) {
                f.setAccessible(true);
                return f.getInt(screen);
            }
        } catch (Throwable ignored) {}
        if (screen instanceof AbstractContainerScreen<?> acs) {
            return acs.getGuiLeft();
        }
        // CNPC carpentry may expose leftPos via field
        try {
            var f = screen.getClass().getField("leftPos");
            return f.getInt(screen);
        } catch (Throwable ignored) {
        }
        try {
            var f = screen.getClass().getField("guiLeft");
            return f.getInt(screen);
        } catch (Throwable ignored) {
        }
        // walk superclasses for leftPos
        Class<?> c = screen.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField("leftPos");
                f.setAccessible(true);
                return f.getInt(screen);
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return -1;
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name) {
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static int getGuiTop(Screen screen) {
        try {
            var f = findField(screen.getClass(), "guiTop");
            if (f != null) {
                f.setAccessible(true);
                return f.getInt(screen);
            }
        } catch (Throwable ignored) {}
        if (screen instanceof AbstractContainerScreen<?> acs) {
            return acs.getGuiTop();
        }
        try {
            var f = screen.getClass().getField("topPos");
            return f.getInt(screen);
        } catch (Throwable ignored) {
        }
        try {
            var f = screen.getClass().getField("guiTop");
            return f.getInt(screen);
        } catch (Throwable ignored) {
        }
        Class<?> c = screen.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField("topPos");
                f.setAccessible(true);
                return f.getInt(screen);
            } catch (Throwable ignored) {
                c = c.getSuperclass();
            }
        }
        return 0;
    }

    /** Call when recipes may have changed while screen open. */
    public static void reloadIfOpen() {
        if (activePanel != null) {
            activePanel.reload();
        }
    }
}