package top.cnpcplus.craftingview;

import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Availability;

public final class RecipeAvailability {
    private RecipeAvailability() {}
    public static Availability copy(Availability source) {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        Availability copy = new Availability();
        copy.load(registries, source.save(registries, new net.minecraft.nbt.CompoundTag()));
        return copy;
    }
    /** Automated workstations use their last real operator; unowned automation cannot bypass conditions. */
    public static boolean allows(Availability availability, Player player) {
        return !availability.hasOptions() || player != null && availability.isAvailable(player);
    }
}
