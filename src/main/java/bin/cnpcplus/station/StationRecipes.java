package bin.cnpcplus.station;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.craftingview.RecipeAvailability;
import bin.cnpcplus.data.NbtFiles;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;

public final class StationRecipes {
    public static final String[] KINDS = {"grindstone", "brewing", "campfire"};
    private static final Map<Integer, NavigableMap<Integer, StationRecipe>> SERVER = new HashMap<>();
    public static final Map<Integer, List<StationRecipe>> CLIENT = new HashMap<>();
    private static final Set<Integer> FAILED = new HashSet<>();
    private static boolean ready;
    private StationRecipes() {}
    public static void start() { ready = true; }
    public static void clear() { NbtFiles.flush(); SERVER.clear(); FAILED.clear(); ready = false; }
    private static Path path(int kind) {
        return noppes.npcs.CustomNpcs.getWorldSaveDirectory().toPath().resolve("data/cnpcplus/recipes/" + KINDS[kind] + "/recipes.dat");
    }
    public static NavigableMap<Integer, StationRecipe> catalogue(int kind) {
        if (!ready) return Collections.emptyNavigableMap();
        return SERVER.computeIfAbsent(kind, key -> {
            NavigableMap<Integer, StationRecipe> map = new TreeMap<>();
            if (!Files.exists(path(key))) return map;
            try {
                NBTTagList list = NbtFiles.read(path(key)).getTagList("Recipes", 10);
                for (int i = 0; i < list.tagCount(); i++) {
                    StationRecipe recipe = StationRecipe.read(list.getCompoundTagAt(i));
                    if (recipe.id >= 0 && recipe.valid(key)) map.put(recipe.id, recipe);
                }
            } catch (IOException | IllegalArgumentException error) {
                FAILED.add(key); CnpcPlus.LOGGER.error("Cannot read {} recipes; original file retained", KINDS[key], error);
            }
            return map;
        });
    }
    public static void persist(int kind) throws IOException {
        if (FAILED.contains(kind)) throw new IOException("Recipe catalogue could not be read");
        NBTTagCompound tag = new NBTTagCompound(); NBTTagList list = new NBTTagList();
        for (StationRecipe recipe : catalogue(kind).values()) list.appendTag(recipe.save(true));
        tag.setTag("Recipes", list); NbtFiles.writeNow(path(kind), tag);
    }
    public static Collection<StationRecipe> list(int kind, boolean client) {
        return client ? CLIENT.getOrDefault(kind, Collections.emptyList()) : catalogue(kind).values();
    }
    public static StationRecipe find(int kind, ItemStack[] inputs, EntityPlayer player) {
        for (StationRecipe recipe : list(kind, player != null && player.world.isRemote))
            if (recipe.matchesInputs(inputs) && RecipeAvailability.allows(recipe.availability, player)) return recipe;
        return null;
    }
    public static boolean hasMatch(int kind, ItemStack[] inputs) {
        for (StationRecipe recipe : catalogue(kind).values()) if (recipe.matchesInputs(inputs)) return true;
        return false;
    }
    public static boolean accepts(int kind, int slot, ItemStack stack, boolean client) {
        for (StationRecipe recipe : list(kind, client))
            if (!recipe.inputs[slot].isEmpty() && stack.getItem() == recipe.inputs[slot].getItem()) return true;
        return false;
    }
}
