package bin.cnpcplus.quest;

import bin.cnpcplus.crafting.CraftKind;
import bin.cnpcplus.crafting.CustomRecipe;
import bin.cnpcplus.crafting.RecipeStore;
import bin.cnpcplus.client.gui.QuestCraftRecipeCatalog;
import bin.cnpcplus.craftingview.RecipeAvailability;
import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import bin.cnpcplus.station.StationRecipe;
import bin.cnpcplus.station.StationRecipes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.Availability;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.junit.Test;
import static org.junit.Assert.*;

/** Calls the real server catalogue without a world or client; only the absent Mixin is stood in. */
public class QuestRecipeCatalogTest {
    static final class Restricted extends Availability {
        Restricted() { minPlayerLevel = Integer.MAX_VALUE; }
        @Override public boolean isAvailable(EntityPlayer player) {
            throw new AssertionError("Quest editing must not evaluate player availability");
        }
    }

    static final class Carpentry extends RecipeCarpentry implements RecipeCarpentryOffsetAccessor {
        int offsetX = 3, offsetY = 3;
        Carpentry() {
            super(1, 1, NonNullList.from(Ingredient.EMPTY, Ingredient.fromStacks(new ItemStack(Items.STICK))),
                    new ItemStack(Items.DIAMOND, 2));
            name = "Restricted recipe"; availability = new Restricted();
        }
        Carpentry(int width, int height, Ingredient... inputs) {
            super(width, height, NonNullList.from(Ingredient.EMPTY, inputs), new ItemStack(Items.DIAMOND, 2));
            name = "Offset diagonal"; availability = new Restricted();
            offsetX = offsetY = 1;
        }
        // CNPC's SRG method needs the game's remapping classloader outside this plain JUnit run.
        @Override public ItemStack getResult() { return getRecipeOutput(); }
        public boolean cnpcplus$isShapeless() { return false; }
        public void cnpcplus$setShapeless(boolean value) {}
        public int cnpcplusGetOffsetX() { return offsetX; }
        public int cnpcplusGetOffsetY() { return offsetY; }
        public boolean cnpcplusHasSavedOffset() { return true; }
        public void cnpcplusSetOffset(int x, int y, boolean saved) {}
    }

    @SuppressWarnings("unchecked")
    @Test public void allNineCataloguesIncludeRestrictedRecipesAndCarpentrySyncKeeps65Entries() throws Exception {
        Bootstrap.register();
        Restricted restriction = new Restricted();
        assertTrue(RecipeAvailability.hasOptions(restriction));
        assertFalse(RecipeAvailability.allows(restriction, null));
        RecipeController previous = RecipeController.instance;
        Field customField = RecipeStore.class.getDeclaredField("DATA"); customField.setAccessible(true);
        Map<CraftKind, NavigableMap<Integer, CustomRecipe>> custom = (Map<CraftKind, NavigableMap<Integer, CustomRecipe>>)customField.get(null);
        Map<CraftKind, NavigableMap<Integer, CustomRecipe>> previousCustom = new HashMap<>(custom);
        Field stationField = StationRecipes.class.getDeclaredField("SERVER"); stationField.setAccessible(true);
        Map<Integer, NavigableMap<Integer, StationRecipe>> stations = (Map<Integer, NavigableMap<Integer, StationRecipe>>)stationField.get(null);
        Map<Integer, NavigableMap<Integer, StationRecipe>> previousStations = new HashMap<>(stations);
        Field cache = SmeltingRecipeRegistry.class.getDeclaredField("cache"); cache.setAccessible(true);
        Object previousCache = cache.get(null);
        Class<?>[] stores = {RecipeStore.class, StationRecipes.class, SmeltingRecipeRegistry.class};
        boolean[] ready = new boolean[stores.length];
        ResourceLocation workbenchId = new ResourceLocation("minecraft", "cnpcplus_restricted_catalogue_test");
        try {
            for (int i = 0; i < stores.length; i++) {
                Field field = stores[i].getDeclaredField("ready"); field.setAccessible(true);
                ready[i] = field.getBoolean(null); field.setBoolean(null, true);
            }
            RecipeController controller = new RecipeController();
            for (int i = 0; i < 65; i++) {
                Carpentry recipe = new Carpentry(); recipe.id = 9000 + i;
                controller.anvilRecipes.put(recipe.id, recipe);
            }
            Carpentry workbench = new Carpentry(2, 2, Ingredient.fromStacks(new ItemStack(Items.STICK, 5)),
                    Ingredient.EMPTY, Ingredient.EMPTY, Ingredient.fromStacks(new ItemStack(Items.IRON_INGOT, 3)));
            workbench.id = 9000; workbench.isGlobal = true;
            controller.globalRecipes.put(workbench.id, workbench);
            // A registry entry outside CNPC's own list must never enter this editing catalogue.
            Carpentry unrelated = new Carpentry(); unrelated.setRegistryName(workbenchId);
            ForgeRegistries.RECIPES.register(unrelated);
            for (CraftKind kind : CraftKind.values()) {
                CustomRecipe recipe = new CustomRecipe(kind);
                recipe.id = 9000; recipe.availability = restriction; recipe.result = new ItemStack(Items.DIAMOND, 2);
                Arrays.fill(recipe.inputs, new ItemStack(Items.STICK));
                NavigableMap<Integer, CustomRecipe> entries = new TreeMap<>(); entries.put(recipe.id, recipe); custom.put(kind, entries);
            }
            for (int kind = 0; kind < 3; kind++) {
                StationRecipe recipe = new StationRecipe(); recipe.id = 9000; recipe.availability = restriction;
                recipe.inputs[0] = new ItemStack(Items.STICK); recipe.outputs[0] = new ItemStack(Items.DIAMOND, 2);
                NavigableMap<Integer, StationRecipe> entries = new TreeMap<>(); entries.put(recipe.id, recipe); stations.put(kind, entries);
            }
            SmeltingRecipeData smelting = new SmeltingRecipeData() {
                // Keep this check about enumeration; native Availability NBT uses SRG methods.
                @Override public SmeltingRecipeData copy() { return this; }
            };
            smelting.id = 9000; smelting.availability = restriction;
            smelting.genericFuelAllowed = true;
            smelting.input = new ItemStack(Items.STICK); smelting.output = new ItemStack(Items.DIAMOND, 2);
            cache.set(null, Collections.singletonMap(smelting.id, smelting));
            Method catalogue = QuestRecipeCatalogPacket.class.getDeclaredMethod("recipes", CraftStation.class);
            catalogue.setAccessible(true);
            for (CraftStation station : CraftStation.values()) {
                List<NBTTagCompound> entries = (List<NBTTagCompound>)catalogue.invoke(null, station);
                assertEquals(station.key + " must contain only CNPC/CNPCplus recipes", station == CraftStation.CARPENTRY ? 65 : 1, entries.size());
                String id = "9000";
                NBTTagCompound entry = entries.stream().filter(tag -> id.equals(tag.getString("Id"))).findFirst().orElse(null);
                assertNotNull(station.key, entry);
                assertEquals(station.key, 2, new ItemStack(entry.getTagList("Outputs", 10).getCompoundTagAt(0)).getCount());
                if (station == CraftStation.WORKBENCH) {
                    assertEquals("Offset diagonal", entry.getString("Name"));
                    assertEquals(3, entry.getInteger("Columns"));
                    NBTTagList grid = entry.getTagList("Inputs", 10);
                    assertEquals(9, grid.tagCount());
                    for (int i = 0; i < 9; i++) {
                        ItemStack input = new ItemStack(grid.getCompoundTagAt(i));
                        if (i == 4 || i == 8) {
                            assertEquals(i == 4 ? Items.STICK : Items.IRON_INGOT, input.getItem());
                            assertEquals(i == 4 ? 5 : 3, input.getCount());
                        } else assertTrue("Empty workbench slot " + i, input.isEmpty());
                    }
                }
                if (station == CraftStation.FURNACE) assertTrue(entry.getBoolean("GenericFuel"));
                if (station == CraftStation.CARPENTRY) {
                    assertEquals(65, entries.size()); assertEquals(4, entry.getInteger("Columns"));
                    assertEquals(16, entry.getTagList("Inputs", 10).tagCount());
                    assertEquals(Items.STICK, new ItemStack(entry.getTagList("Inputs", 10).getCompoundTagAt(15)).getItem());
                    assertTrue(entries.stream().anyMatch(tag -> "9064".equals(tag.getString("Id"))));
                    for (int from = 0; from < entries.size(); from += 64) {
                        NBTTagList page = new NBTTagList();
                        for (int i = from; i < Math.min(from + 64, entries.size()); i++) page.appendTag(entries.get(i));
                        QuestCraftRecipeCatalog.receive(station, from > 0, page);
                    }
                    Method received = QuestCraftRecipeCatalog.class.getDeclaredMethod("recipes", CraftStation.class);
                    received.setAccessible(true);
                    assertEquals(65, ((List<?>)received.invoke(null, station)).size());
                }
            }
        } finally {
            RecipeController.instance = previous;
            custom.clear(); custom.putAll(previousCustom); stations.clear(); stations.putAll(previousStations);
            cache.set(null, previousCache);
            ((IForgeRegistryModifiable<IRecipe>)ForgeRegistries.RECIPES).remove(workbenchId);
            for (int i = 0; i < stores.length; i++) {
                Field field = stores[i].getDeclaredField("ready"); field.setAccessible(true); field.setBoolean(null, ready[i]);
            }
        }
    }
}
