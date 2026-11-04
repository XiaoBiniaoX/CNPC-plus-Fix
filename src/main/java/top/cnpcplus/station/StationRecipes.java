package top.cnpcplus.station;

import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.cnpcplus.craftingview.RecipeAvailability;

public final class StationRecipes {
    public static final String[] KINDS = {"grindstone", "brewing", "campfire"};
    private static final Map<Integer, LinkedHashMap<Integer, StationRecipe>> SERVER = new HashMap<>();
    public static final Map<Integer, List<StationRecipe>> CLIENT = new HashMap<>();
    private StationRecipes() {}

    public static Map<Integer, StationRecipe> catalogue(int kind) {
        return SERVER.computeIfAbsent(kind, key -> {
            LinkedHashMap<Integer, StationRecipe> recipes = new LinkedHashMap<>();
            File file = file(key);
            if (file.isFile()) {
                try (var input = new FileInputStream(file)) {
                    ListTag list = NbtIo.readCompressed(input).getList("Recipes", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) { StationRecipe r = StationRecipe.read(list.getCompound(i)); recipes.put(r.id, r); }
                } catch (IOException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("Cannot read {} recipes", KINDS[key], e); }
            }
            return recipes;
        });
    }

    private static File file(int kind) { return new File(noppes.npcs.CustomNpcs.getLevelSaveDirectory(), "data/cnpcplus/recipes/" + KINDS[kind] + "/recipes.dat"); }
    public static void persist(int kind) throws IOException {
        File target = file(kind), dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (StationRecipe recipe : catalogue(kind).values()) list.add(recipe.save(true));
        tag.put("Recipes", list);
        File temp = new File(dir, "recipes.tmp");
        try (var output = new FileOutputStream(temp)) { NbtIo.writeCompressed(tag, output); }
        top.cnpcplus.persist.FileReplacement.replace(temp.toPath(), target.toPath());
    }

    public static StationRecipe find(int kind, ItemStack[] inputs, Player player) {
        for (StationRecipe r : list(kind, player != null && player.level().isClientSide)) {
            if (!RecipeAvailability.allows(r.availability, player)) continue;
            if (r.matchesInputs(inputs)) return r;
        }
        return null;
    }

    public static Collection<StationRecipe> list(int kind, boolean client) {
        return client ? CLIENT.getOrDefault(kind, List.of()) : catalogue(kind).values();
    }

    public static void clear() { SERVER.clear(); }

    public static boolean hasMatch(int kind, ItemStack[] inputs) {
        return catalogue(kind).values().stream().anyMatch(r -> r.matchesInputs(inputs));
    }
    public static boolean accepts(int kind, int slot, ItemStack stack, boolean output) {
        var recipes = list(kind, net.minecraftforge.fml.util.thread.SidedThreadGroups.SERVER != Thread.currentThread().getThreadGroup());
        for (StationRecipe recipe : recipes) {
            if (!recipe.inputs[slot].isEmpty() && stack.is(recipe.inputs[slot].getItem())) return true;
            if (output && slot < 3 && !recipe.outputs[slot].isEmpty() && stack.is(recipe.outputs[slot].getItem())) return true;
        }
        return false;
    }
}
