package top.cnpcplus.craftingview;

import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Availability;

public final class RecipeAvailability {
    private RecipeAvailability() {}
    /** Automated workstations use their last real operator; unowned automation cannot bypass conditions. */
    public static boolean allows(Availability availability, Player player) {
        return !availability.hasOptions() || player != null && availability.isAvailable(player);
    }
}
