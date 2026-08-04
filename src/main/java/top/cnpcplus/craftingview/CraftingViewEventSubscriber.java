package top.cnpcplus.craftingview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.client.gui.player.GuiNpcCarpentryBench;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import top.cnpcplus.CnpcPlus;
import top.cnpcplus.craftingview.network.PacketFillCraftingGrid;
import top.cnpcplus.craftingview.network.PacketHandler;
import top.cnpcplus.mixin.AbstractContainerScreenAccess;

import java.util.List;

@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CraftingViewEventSubscriber {

    private static RecipePanel activePanel = null;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTickStart(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (activePanel == null) return;
        if (Minecraft.getInstance().screen == null) {
            activePanel = null;
        }
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen instanceof GuiNpcCarpentryBench || screen instanceof CraftingScreen) {
            activePanel = new RecipePanel(screen instanceof GuiNpcCarpentryBench);
            return;
        }
        activePanel = null;
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (activePanel == null) return;
        Screen screen = event.getScreen();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;
        RecipePanelRenderer.render(event.getGuiGraphics(), screen, activePanel,
                (int) event.getMouseX(), (int) event.getMouseY(), gl, gt);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (activePanel == null) return;
        Screen screen = event.getScreen();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;
        int mx = (int) event.getMouseX();
        int my = (int) event.getMouseY();
        if (RecipePanelRenderer.isPanelHit(activePanel, gl, gt, mx, my)) {
            activePanel.scroll(event.getScrollDelta() > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (activePanel == null) return;
        Screen screen = event.getScreen();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;
        if (event.getButton() != 0) return;
        int mx = (int) event.getMouseX();
        int my = (int) event.getMouseY();

        if (!RecipePanelRenderer.isPanelHit(activePanel, gl, gt, mx, my)) {
            activePanel.searchField.setFocused(false);
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
                    PacketHandler.CHANNEL.sendToServer(new PacketFillCraftingGrid(recipe.id));
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

    private static int getGuiLeft(Screen screen) {
        if (screen instanceof GuiContainerNPCInterface g) return g.guiLeft;
        if (screen instanceof AbstractContainerScreen<?> a) return ((AbstractContainerScreenAccess)a).cnpcplus$getLeftPos();
        return -1;
    }

    private static int getGuiTop(Screen screen) {
        if (screen instanceof GuiContainerNPCInterface g) return g.guiTop;
        if (screen instanceof AbstractContainerScreen<?> a) return ((AbstractContainerScreenAccess)a).cnpcplus$getTopPos();
        return -1;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
