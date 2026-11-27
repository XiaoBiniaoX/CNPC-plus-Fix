package top.cnpcplus.client.gui;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import top.cnpcplus.smelting.FuelMatcher;
import top.cnpcplus.smelting.SmeltingRecipeData;
import top.cnpcplus.smelting.client.SmeltingClientData;

/** Headless regression for the per-recipe smelting fuel data shown by quest recipe selection. */
public final class QuestCraftRecipeRegression {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage())) throw unavailable;
            System.out.println("HEADLESS_ONLY: untransformed Forge network hook; not a loader smoke test");
        }

        SmeltingRecipeData emptyFuel = recipe(1, ItemStack.EMPTY, false, true);
        SmeltingRecipeData specifiedFuel = recipe(2, new ItemStack(Items.DIAMOND), true, false);
        SmeltingRecipeData genericFuel = recipe(3, ItemStack.EMPTY, true, true);
        SmeltingClientData.set(List.of(emptyFuel, specifiedFuel, genericFuel));

        QuestCraftRecipe empty = find("furnace", "1");
        check(empty.inputs().size() == 2 && empty.inputAt(0).is(Items.IRON_ORE)
                        && empty.inputAt(1).isEmpty() && !empty.genericFuelAllowed(),
                "empty fuel slot or disabled generic-fuel flag was discarded");
        QuestCraftRecipe specified = find("furnace", "2");
        check(specified.inputAt(1).is(Items.DIAMOND) && specified.genericFuelAllowed(),
                "specified fuel and this recipe's generic-fuel flag were not carried into the preview");
        QuestCraftRecipe generic = find("furnace", "3");
        check(generic.inputAt(1).isEmpty() && generic.genericFuelAllowed(),
                "empty specified-fuel slot with generic fuel enabled was not represented");
        check(find("blast_furnace", "1") != null && find("smoker", "2") == null,
                "per-station blast-furnace/smoker availability was not preserved");

        ItemStack coal = new ItemStack(Items.COAL);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        check(FuelMatcher.specified(ItemStack.EMPTY, 200).burnTime(coal, RecipeType.SMELTING) == 0,
                "an empty specified-fuel slot incorrectly removed the fuel constraint");
        int genericBurnTime = FuelMatcher.generic().burnTime(coal, RecipeType.SMELTING);
        check(FuelMatcher.either(ItemStack.EMPTY, 200).burnTime(coal, RecipeType.SMELTING) == genericBurnTime,
                "empty specified-fuel slot with generic enabled did not delegate to ForgeHooks");
        FuelMatcher enabledWithSpecified = FuelMatcher.either(diamond, 200);
        check(enabledWithSpecified.burnTime(diamond, RecipeType.SMELTING) == 200
                        && enabledWithSpecified.burnTime(coal, RecipeType.SMELTING) == genericBurnTime,
                "generic fuel replaced the specified fuel instead of adding to it");

        SmeltingRecipeData loaded = SmeltingRecipeData.fromNBT(specifiedFuel.toNBT());
        check(loaded.fuel.is(Items.DIAMOND) && loaded.genericFuelAllowed,
                "specified fuel or generic-fuel flag failed to survive NBT storage/sync format");
        System.out.println("QUEST_CRAFT_FUEL_REGRESSION=PASS");
    }

    private static SmeltingRecipeData recipe(int id, ItemStack fuel, boolean generic, boolean smokerAllowed) {
        SmeltingRecipeData data = new SmeltingRecipeData();
        data.id = id;
        data.name = "Recipe " + id;
        data.input = new ItemStack(Items.IRON_ORE);
        data.fuel = fuel.copy();
        data.output = new ItemStack(Items.IRON_INGOT);
        data.blastAllowed = true;
        data.smokerAllowed = smokerAllowed;
        data.genericFuelAllowed = generic;
        return data;
    }

    private static QuestCraftRecipe find(String station, String id) {
        return QuestCraftRecipeCatalog.recipes(station).stream()
                .filter(recipe -> recipe.id().equals(id)).findFirst().orElse(null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
