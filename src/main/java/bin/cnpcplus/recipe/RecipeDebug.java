package bin.cnpcplus.recipe;

import bin.cnpcplus.CnpcPlus;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.Map;

public final class RecipeDebug {
    public static boolean ENABLED = false;

    private RecipeDebug() {}

    public static boolean enabled() {
        return ENABLED;
    }

    public static void info(String fmt, Object... args) {
        if (!ENABLED) return;
        CnpcPlus.LOGGER.info("[RecipeDebug] " + fmt, args);
    }

    public static void probeAllGlobals() {
        if (!ENABLED) return;
        RecipeController c = RecipeController.instance;
        if (c == null || c.globalRecipes == null) {
            info("probeAll: no controller/global");
            return;
        }
        info("probeAll Storage anvil={} global={}",
                c.anvilRecipes != null ? Integer.valueOf(c.anvilRecipes.size()) : Integer.valueOf(-1),
                Integer.valueOf(c.globalRecipes.size()));
        for (Map.Entry<Integer, RecipeCarpentry> e : c.globalRecipes.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null) continue;
            info("global id={} name={} valid={}", e.getKey(), r.name, Boolean.valueOf(r.isValid()));
        }
    }
}
