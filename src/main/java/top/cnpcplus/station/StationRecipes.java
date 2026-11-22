package top.cnpcplus.station;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.cnpcplus.craftingview.RecipeAvailability;
import top.cnpcplus.craftingview.RecipeDiskWrites;

public final class StationRecipes {
    public static final String[] KINDS = {"grindstone", "brewing", "campfire"};
    private static final Map<Integer, LinkedHashMap<Integer, StationRecipe>> SERVER = new HashMap<>();
    static final boolean[] saving = new boolean[KINDS.length];
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
    static CompoundTag snapshot(Collection<StationRecipe> recipes) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (StationRecipe recipe : recipes) list.add(recipe.save(true));
        tag.put("Recipes", list);
        return tag;
    }

    static CompletableFuture<Void> persist(int kind, CompoundTag tag) {
        return RecipeDiskWrites.save(file(kind).toPath(), tag, false);
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

    public static void clear() {
        SERVER.clear();
        Arrays.fill(saving, false);
    }

    public static boolean hasMatch(int kind, ItemStack[] inputs) {
        return catalogue(kind).values().stream().anyMatch(r -> r.matchesInputs(inputs));
    }
    public static boolean accepts(int kind, int slot, ItemStack stack, boolean output) {
        var recipes = list(kind, net.minecraftforge.fml.util.thread.SidedThreadGroups.SERVER != Thread.currentThread().getThreadGroup());
        for (StationRecipe recipe : recipes) {
            int interchangeable = kind == 0 ? 2 : 3;
            if (recipe.shapeless && slot < interchangeable) {
                for (int i = 0; i < interchangeable; i++) {
                    if (stack.is(recipe.inputs[i].getItem()) || output && stack.is(recipe.outputs[i].getItem())) return true;
                }
            } else if (!recipe.inputs[slot].isEmpty() && stack.is(recipe.inputs[slot].getItem())) return true;
            if (output && slot < 3 && !recipe.outputs[slot].isEmpty() && stack.is(recipe.outputs[slot].getItem())) return true;
        }
        return false;
    }
}
