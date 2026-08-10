package bin.cnpcplus.recipe.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.client.gui.util.GuiNpcButton;

import java.util.HashMap;
import java.util.Map;

/** Client-side cache of which recipe syncIds are in shared_recipes.dat */
public final class PersistClientState {
    private static final Map<Integer, Boolean> map = new HashMap<Integer, Boolean>();

    private PersistClientState() {}

    public static void set(int syncId, boolean persisted) {
        if (syncId <= 0) return;
        map.put(Integer.valueOf(syncId), Boolean.valueOf(persisted));
    }

    public static boolean isPersisted(int syncId) {
        Boolean b = map.get(Integer.valueOf(syncId));
        return b != null && b.booleanValue();
    }

    public static boolean known(int syncId) {
        return map.containsKey(Integer.valueOf(syncId));
    }

    public static void clear() {
        map.clear();
    }

    /** Apply server state and refresh open recipe GUI buttons. */
    public static void applyFromServer(final int syncId, final boolean persisted) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                set(syncId, persisted);
                refreshOpenGui();
            }
        });
    }

    public static void refreshOpenGui() {
        try {
            GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (!(screen instanceof GuiNpcManageRecipes)) return;
            GuiNpcManageRecipes gui = (GuiNpcManageRecipes) screen;
            // Re-run enabled/labels via button ids 7/8 using current PersistClientState
            // Actual enable logic lives in mixin refresh; call setEnabled through known pattern:
            // mixin stores nothing static — poke buttons if present.
            // Full refresh is done by re-querying selection from scroll via package-private fields —
            // simplest: fire a no-op setSelected if we can read selected via reflection.
            Object selected = null;
            try {
                java.lang.reflect.Field f = GuiNpcManageRecipes.class.getDeclaredField("selected");
                f.setAccessible(true);
                selected = f.get(gui);
            } catch (Throwable ignored) {
            }
            int syncId = -1;
            try {
                java.lang.reflect.Field fd = GuiNpcManageRecipes.class.getDeclaredField("data");
                fd.setAccessible(true);
                Object data = fd.get(gui);
                if (selected instanceof String && data instanceof Map) {
                    Object v = ((Map) data).get(selected);
                    if (v instanceof Integer) syncId = ((Integer) v).intValue();
                }
            } catch (Throwable ignored) {
            }
            GuiNpcButton persistBtn = gui.getButton(7);
            GuiNpcButton unpersistBtn = gui.getButton(8);
            boolean has = syncId > 0;
            boolean p = has && isPersisted(syncId);
            if (persistBtn != null) {
                persistBtn.setEnabled(has && !p);
                persistBtn.displayString = label("cnpcplus.recipe.persist", "Persist");
            }
            if (unpersistBtn != null) {
                unpersistBtn.setEnabled(has && p);
                unpersistBtn.displayString = label("cnpcplus.recipe.unpersist", "Unpersist");
            }
        } catch (Throwable ignored) {
        }
    }

    private static String label(String key, String fallback) {
        try {
            String s = net.minecraft.util.text.translation.I18n.translateToLocal(key);
            if (s == null || s.isEmpty() || s.equals(key) || s.startsWith("cnpcplus.")) return fallback;
            return s;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
