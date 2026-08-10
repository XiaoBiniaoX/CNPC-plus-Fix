package bin.cnpcplus.recipe.sync;

import bin.cnpcplus.recipe.id.RecipeIds;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.NoppesUtilServer;

import java.util.HashMap;
import java.util.Map;

/**
 * Network list/single push only. No disk, no matching.
 */
public final class RecipeSync {
    private RecipeSync() {}

    /**
     * Restores behavior of SPacketRecipesGet.sendRecipeData (upstream for-loops empty).
     *
     * @param size 3 = global, 4 = anvil
     */
    public static void sendRecipeList(ServerPlayer player, int size) {
        Map<String, Integer> map;
        if (size == 3) {
            map = RecipeIds.INSTANCE.scrollMapGlobal();
        } else {
            map = RecipeIds.INSTANCE.scrollMapAnvil();
        }
        NoppesUtilServer.sendScrollData(player, new HashMap<>(map));
    }
}
