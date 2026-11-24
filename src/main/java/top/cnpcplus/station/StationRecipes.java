package top.cnpcplus.station;

import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import top.cnpcplus.craftingview.RecipeAvailability;

@EventBusSubscriber(modid = "cnpcplus")
public final class StationRecipes {
    public static final String[] KINDS = {"grindstone", "brewing", "campfire"};
    private static final Map<Integer, LinkedHashMap<Integer, StationRecipe>> SERVER = new HashMap<>();
    public static final Map<Integer, List<StationRecipe>> CLIENT = new HashMap<>();
    public static final boolean[] saving = new boolean[3];
    private StationRecipes() {}
    public static Map<Integer, StationRecipe> catalogue(int kind) {
        return SERVER.computeIfAbsent(kind, key -> {
            LinkedHashMap<Integer, StationRecipe> recipes = new LinkedHashMap<>();
            File file = file(key);
            if (file.isFile()) {
                try (var input = new FileInputStream(file)) {
                    ListTag list = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()).getList("Recipes", Tag.TAG_COMPOUND);
                    var registries = ServerLifecycleHooks.getCurrentServer().registryAccess();
                    for (int i = 0; i < list.size(); i++) { StationRecipe r = StationRecipe.read(list.getCompound(i), registries); recipes.put(r.id, r); }
                } catch (IOException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("Cannot read {} recipes", KINDS[key], e); }
            }
            return recipes;
        });
    }
    private static File file(int kind) { return new File(noppes.npcs.CustomNpcs.getLevelSaveDirectory(), "data/cnpcplus/recipes/" + KINDS[kind] + "/recipes.dat"); }
    public static java.util.concurrent.CompletableFuture<Void> persist(int kind, Collection<StationRecipe> recipes) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        var registries = ServerLifecycleHooks.getCurrentServer().registryAccess();
        for (StationRecipe recipe : recipes) list.add(recipe.save(registries));
        tag.put("Recipes", list);
        return top.cnpcplus.craftingview.RecipeDiskWrites.save(file(kind).toPath(), tag, false);
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
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVER.clear(); Arrays.fill(saving, false); }
    public static boolean hasMatch(int kind, ItemStack[] inputs) { return catalogue(kind).values().stream().anyMatch(r -> r.matchesInputs(inputs)); }
    public static boolean accepts(int kind, int slot, ItemStack stack, boolean output) {
        var recipes = list(kind, net.neoforged.fml.util.thread.SidedThreadGroups.SERVER != Thread.currentThread().getThreadGroup());
        for (StationRecipe recipe : recipes) {
            if (recipe.shapeless && (kind == 0 || kind == 1 && slot < 3)) {
                int count = kind == 0 ? 2 : 3;
                for (int i = 0; i < count; i++) {
                    if (!recipe.inputs[i].isEmpty() && stack.is(recipe.inputs[i].getItem())) return true;
                    if (output && !recipe.outputs[i].isEmpty() && stack.is(recipe.outputs[i].getItem())) return true;
                }
            }
            if (!recipe.inputs[slot].isEmpty() && stack.is(recipe.inputs[slot].getItem())) return true;
            if (output && slot < 3 && !recipe.outputs[slot].isEmpty() && stack.is(recipe.outputs[slot].getItem())) return true;
        }
        return false;
    }
}
