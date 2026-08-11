package bin.cnpcplus.recipe.sync;

import bin.cnpcplus.recipe.id.RecipeIds;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.NoppesUtilServer;

import java.util.HashMap;
import java.util.Map;

public final class RecipeSync {
    private RecipeSync() {}

    /**
     * size 3 = global, 4 = anvil
     */
    public static void sendRecipeList(EntityPlayerMP player, int size) {
        Map<String, Integer> map;
        if (size == 3) {
            map = RecipeIds.INSTANCE.scrollMapGlobal();
        } else {
            map = RecipeIds.INSTANCE.scrollMapAnvil();
        }
        if (map == null || map.isEmpty()) {
            // fallback to controller maps
            map = new HashMap<String, Integer>();
            if (noppes.npcs.controllers.RecipeController.instance != null) {
                if (size == 3) {
                    for (noppes.npcs.controllers.data.RecipeCarpentry r
                            : noppes.npcs.controllers.RecipeController.instance.globalRecipes.values()) {
                        if (r != null && r.name != null) map.put(r.name, Integer.valueOf(r.id));
                    }
                } else {
                    for (noppes.npcs.controllers.data.RecipeCarpentry r
                            : noppes.npcs.controllers.RecipeController.instance.anvilRecipes.values()) {
                        if (r != null && r.name != null) map.put(r.name, Integer.valueOf(r.id));
                    }
                }
            }
        }
        NoppesUtilServer.sendScrollData(player, new HashMap<String, Integer>(map));
    }
}
