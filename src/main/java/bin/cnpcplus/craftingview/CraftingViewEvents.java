package bin.cnpcplus.craftingview;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.craftingview.network.PacketFillCraftingGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.client.gui.player.GuiNpcCarpentryBench;

import java.lang.reflect.Field;
import java.util.List;

@Mod.EventBusSubscriber(modid = CnpcPlus.MODID, value = Side.CLIENT)
public final class CraftingViewEvents {
    private static RecipePanel activePanel;
    private static int reloadTicker;
    private static String lastSearch = "";

    private CraftingViewEvents() {}

    @SubscribeEvent
    public static void onGuiOpen(GuiOpenEvent event) {
        GuiScreen screen = event.getGui();
        if (screen instanceof GuiNpcCarpentryBench) {
            activePanel = new RecipePanel(true);
            lastSearch = "";
        } else if (screen instanceof GuiCrafting) {
            activePanel = new RecipePanel(false);
            lastSearch = "";
        } else {
            activePanel = null;
        }
    }

    @SubscribeEvent
    public static void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (activePanel == null) return;
        if ((++reloadTicker % 40) == 0) {
            activePanel.reload();
        }
        String cur = activePanel.searchField.getText();
        if (cur == null) cur = "";
        if (!cur.equals(lastSearch)) {
            lastSearch = cur;
            activePanel.rebuildFiltered();
        }
        GuiScreen screen = event.getGui();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;
        RecipePanelRenderer.render(screen, activePanel, event.getMouseX(), event.getMouseY(), gl, gt);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (activePanel == null) return;
        GuiScreen screen = event.getGui();
        int gl = getGuiLeft(screen);
        int gt = getGuiTop(screen);
        if (gl < 0) return;

        int mx = org.lwjgl.input.Mouse.getEventX() * screen.width / Minecraft.getMinecraft().displayWidth;
        int my = screen.height - org.lwjgl.input.Mouse.getEventY() * screen.height / Minecraft.getMinecraft().displayHeight - 1;

        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0 && RecipePanelRenderer.isPanelHit(activePanel, gl, gt, mx, my)) {
            activePanel.scroll(dWheel > 0 ? -1 : 1);
            event.setCanceled(true);
            return;
        }

        if (!org.lwjgl.input.Mouse.getEventButtonState()) return;
        if (org.lwjgl.input.Mouse.getEventButton() != 0) return;

        if (!RecipePanelRenderer.isPanelHit(activePanel, gl, gt, mx, my)) {
            activePanel.searchField.setFocused(false);
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
                    CraftingViewNetwork.CHANNEL.sendToServer(new PacketFillCraftingGrid(recipe.id));
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
    public static void onKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (activePanel == null || activePanel.isCollapsed()) return;
        if (!activePanel.searchField.isFocused()) return;
        char c = org.lwjgl.input.Keyboard.getEventCharacter();
        int key = org.lwjgl.input.Keyboard.getEventKey();
        if (org.lwjgl.input.Keyboard.getEventKeyState() || key == 0 && Character.isDefined(c)) {
            if (activePanel.searchField.textboxKeyTyped(c, key)) {
                activePanel.rebuildFiltered();
                event.setCanceled(true);
            }
        }
    }

    private static void playClick() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static int getGuiLeft(GuiScreen screen) {
        if (screen instanceof GuiContainer) {
            try {
                Field f = findField(GuiContainer.class, "guiLeft", "field_147003_i");
                if (f != null) {
                    f.setAccessible(true);
                    return f.getInt(screen);
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Field f = findField(screen.getClass(), "guiLeft", "field_147003_i");
            if (f != null) {
                f.setAccessible(true);
                return f.getInt(screen);
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static int getGuiTop(GuiScreen screen) {
        if (screen instanceof GuiContainer) {
            try {
                Field f = findField(GuiContainer.class, "guiTop", "field_147009_r");
                if (f != null) {
                    f.setAccessible(true);
                    return f.getInt(screen);
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Field f = findField(screen.getClass(), "guiTop", "field_147009_r");
            if (f != null) {
                f.setAccessible(true);
                return f.getInt(screen);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static Field findField(Class<?> c, String... names) {
        while (c != null) {
            for (int i = 0; i < names.length; i++) {
                try {
                    return c.getDeclaredField(names[i]);
                } catch (NoSuchFieldException ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    public static void reloadIfOpen() {
        if (activePanel != null) {
            activePanel.reload();
        }
    }
}
