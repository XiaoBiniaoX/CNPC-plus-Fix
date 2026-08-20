package bin.cnpcplus.smelting;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Applies the per-recipe burn time.
 *
 * TileEntityFurnace.getItemBurnTime is static and has no furnace to inspect, so
 * the recipe cannot be identified from the item alone. The furnace currently
 * being ticked is published by MixinTileEntityFurnaceBurnContext and read back
 * here, which is what lets a recipe say "only this fuel, for exactly this long".
 *
 * Falling back to the vanilla value whenever no custom recipe applies keeps
 * ordinary furnaces untouched.
 */
public final class SmeltingBurnTimeHandler {
    /** Set for the duration of one furnace tick, on the server thread. */
    private static final ThreadLocal<TileEntityFurnace> CURRENT =
            new ThreadLocal<TileEntityFurnace>();

    public static void beginFurnace(TileEntityFurnace furnace) {
        CURRENT.set(furnace);
    }

    public static void endFurnace() {
        CURRENT.remove();
    }

    /**
     * event.getBurnTime() is deliberately not consulted. Forge seeds it from
     * Item.getItemBurnTime, which returns -1 ("no opinion") for anything that is
     * not custom fuel, so it is not a usable stand-in for the vanilla number.
     * Leaving the event untouched is what falls back to vanilla behaviour.
     */
    @SubscribeEvent
    public void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        TileEntityFurnace furnace = CURRENT.get();
        if (furnace == null) {
            return;
        }
        SmeltingRecipeData data = SmeltingFuelRules.findForFurnace(furnace);
        if (data == null) {
            return;
        }
        Integer custom = SmeltingFuelRules.burnTimeFor(data, event.getItemStack());
        if (custom != null) {
            event.setBurnTime(custom.intValue());
        }
    }
}
