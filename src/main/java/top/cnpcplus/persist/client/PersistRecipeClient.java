package top.cnpcplus.persist.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import top.cnpcplus.persist.PersistedRecipeStore;

public final class PersistRecipeClient {

    public static final int BTN_PERSIST = 100;
    public static final int BTN_UNPERSIST = 101;

    private PersistRecipeClient() {}

    public static void refreshButtons() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GuiNpcManageRecipes gui)) return;
        GuiButtonNop persist = gui.getButton(BTN_PERSIST);
        GuiButtonNop unpersist = gui.getButton(BTN_UNPERSIST);
        if (persist == null || unpersist == null) return;

        ContainerManageRecipes container;
        try {
            java.lang.reflect.Field f = GuiNpcManageRecipes.class.getDeclaredField("container");
            f.setAccessible(true);
            container = (ContainerManageRecipes) f.get(gui);
        } catch (Exception e) {
            return;
        }
        if (container == null || container.recipe == null || container.recipe.getId() == null) {
            persist.setEnabled(false);
            unpersist.setEnabled(false);
            return;
        }
        ResourceLocation id = container.recipe.getId();
        boolean on = PersistedRecipeStore.clientContains(id);
        persist.setEnabled(!on);
        unpersist.setEnabled(on);
    }
}
