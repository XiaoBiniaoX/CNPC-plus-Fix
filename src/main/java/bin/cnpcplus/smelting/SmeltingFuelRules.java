package bin.cnpcplus.smelting;

import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

/**
 * Matching rules shared by every furnace hook.
 *
 * 1.12.2 has a single furnace (TileEntityFurnace), so blastAllowed and
 * smokerAllowed are stored but never consulted here: there is no blast furnace
 * or smoker to consult them for.
 */
public final class SmeltingFuelRules {
    /** Furnace slot layout is fixed in 1.12.2: 0 input, 1 fuel, 2 output. */
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;

    private SmeltingFuelRules() {}

    /** Compares item, metadata and NBT, so a specific fuel really is specific. */
    public static boolean stackMatches(ItemStack actual, ItemStack expected) {
        if (actual == null || expected == null || actual.isEmpty() || expected.isEmpty()) {
            return false;
        }
        return ItemStack.areItemsEqual(actual, expected)
                && ItemStack.areItemStackTagsEqual(actual, expected);
    }

    /** The custom recipe whose input matches this stack, or null. */
    public static SmeltingRecipeData findByInput(ItemStack input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        List<SmeltingRecipeData> all = SmeltingRecipeRegistry.list();
        for (int i = 0; i < all.size(); ++i) {
            SmeltingRecipeData data = all.get(i);
            if (data != null && !data.output.isEmpty() && stackMatches(input, data.input)) {
                return data;
            }
        }
        return null;
    }

    public static SmeltingRecipeData findForFurnace(TileEntityFurnace furnace) {
        if (furnace == null) {
            return null;
        }
        return findByInput(furnace.getStackInSlot(SLOT_INPUT));
    }

    /**
     * Burn time this fuel is worth for the given recipe.
     *
     * Returns null to mean "no opinion, let vanilla decide", and 0 to mean
     * "explicitly not fuel for this recipe".
     *
     * The specified fuel always burns, in both modes. It is usually an item the
     * game scores as not-fuel at all (armour, rails, a diamond), so the vanilla
     * value cannot be reused or the furnace would never light; the recipe's cook
     * time is used instead, which is exactly enough for one item.
     *
     * genericFuelAllowed then decides what happens to *everything else*:
     *   on  - ordinary fuels work too, so defer to vanilla and whatever other
     *         mods registered (null).
     *   off - only the specified fuel works, so refuse everything else (0).
     *
     * Note this is deliberately not the same as the 1.20.1 implementation, whose
     * FuelMatcher treats the two modes as mutually exclusive (generic() replaces
     * specified() rather than adding to it).
     */
    public static Integer burnTimeFor(SmeltingRecipeData data, ItemStack fuel) {
        if (data == null || fuel == null || fuel.isEmpty()) {
            return null;
        }
        if (data.fuel.isEmpty()) {
            // No fuel named, so there is nothing to restrict to.
            return null;
        }
        if (stackMatches(fuel, data.fuel)) {
            return Integer.valueOf(Math.max(1, Math.round(data.cookTime)));
        }
        return data.genericFuelAllowed ? null : Integer.valueOf(0);
    }
}
