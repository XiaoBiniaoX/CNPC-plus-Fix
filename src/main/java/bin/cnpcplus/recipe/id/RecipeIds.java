package bin.cnpcplus.recipe.id;

import net.minecraft.resources.ResourceLocation;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Identity only: ResourceLocation, syncId, displayName lookups.
 */
public final class RecipeIds {
    public static final RecipeIds INSTANCE = new RecipeIds();

    private final Map<Integer, ResourceLocation> bySyncId = new HashMap<>();
    private final Map<ResourceLocation, Integer> byLocation = new HashMap<>();
    private final Map<String, Integer> byNameLower = new HashMap<>();
    private final Map<Integer, RecipeCarpentry> bySyncRecipe = new HashMap<>();
    private final AtomicInteger nextSyncId = new AtomicInteger(1);

    private RecipeIds() {}

    public void clear() {
        bySyncId.clear();
        byLocation.clear();
        byNameLower.clear();
        bySyncRecipe.clear();
        nextSyncId.set(1);
    }

    /** Stable map key: customnpcs:recipe/id_<syncId> */
    public ResourceLocation locationOf(int syncId) {
        return ResourceLocation.fromNamespaceAndPath("customnpcs", "recipe/id_" + syncId);
    }

    public ResourceLocation locationOf(RecipeCarpentry recipe) {
        Integer sync = syncIdOfRecipe(recipe);
        if (sync == null) {
            return ResourceLocation.fromNamespaceAndPath("customnpcs", "recipe/tmp_" + System.identityHashCode(recipe));
        }
        return locationOf(sync);
    }

    public Integer syncIdOfRecipe(RecipeCarpentry recipe) {
        if (recipe == null) return null;
        for (Map.Entry<Integer, RecipeCarpentry> e : bySyncRecipe.entrySet()) {
            if (e.getValue() == recipe) return e.getKey();
        }
        return null;
    }

    public int register(RecipeCarpentry recipe) {
        Integer existing = syncIdOfRecipe(recipe);
        if (existing != null) {
            return rebind(existing, recipe);
        }
        if (recipe.name != null) {
            Integer byName = byNameLower.get(recipe.name.toLowerCase(Locale.ROOT));
            if (byName != null) {
                return rebind(byName, recipe);
            }
        }
        int syncId = nextSyncId.getAndIncrement();
        return rebind(syncId, recipe);
    }

    public int register(RecipeCarpentry recipe, int preferredSyncId) {
        if (preferredSyncId > 0) {
            setNextSyncFloor(preferredSyncId);
            return rebind(preferredSyncId, recipe);
        }
        return register(recipe);
    }

    private int rebind(int syncId, RecipeCarpentry recipe) {
        RecipeCarpentry prev = bySyncRecipe.get(syncId);
        if (prev != null && prev.name != null) {
            byNameLower.remove(prev.name.toLowerCase(Locale.ROOT));
        }
        ResourceLocation oldLoc = bySyncId.get(syncId);
        if (oldLoc != null) {
            byLocation.remove(oldLoc);
        }
        ResourceLocation loc = locationOf(syncId);
        bySyncId.put(syncId, loc);
        byLocation.put(loc, syncId);
        bySyncRecipe.put(syncId, recipe);
        if (recipe.name != null) {
            byNameLower.put(recipe.name.toLowerCase(Locale.ROOT), syncId);
        }
        return syncId;
    }

    public void unregister(int syncId) {
        ResourceLocation loc = bySyncId.remove(syncId);
        RecipeCarpentry r = bySyncRecipe.remove(syncId);
        if (loc != null) byLocation.remove(loc);
        if (r != null && r.name != null) {
            byNameLower.remove(r.name.toLowerCase(Locale.ROOT));
        }
    }

    public RecipeCarpentry bySyncId(int syncId) {
        return bySyncRecipe.get(syncId);
    }

    public Integer syncIdByName(String name) {
        if (name == null) return null;
        return byNameLower.get(name.toLowerCase(Locale.ROOT));
    }

    public ResourceLocation locationBySyncId(int syncId) {
        return bySyncId.get(syncId);
    }

    public Map<String, Integer> scrollMapAnvil() {
        return scrollMap(false);
    }

    public Map<String, Integer> scrollMapGlobal() {
        return scrollMap(true);
    }

    private Map<String, Integer> scrollMap(boolean global) {
        Map<String, Integer> map = new HashMap<>();
        for (Map.Entry<Integer, RecipeCarpentry> e : bySyncRecipe.entrySet()) {
            RecipeCarpentry r = e.getValue();
            if (r == null || r.name == null) continue;
            if (r.isGlobal != global) continue;
            map.put(r.name, e.getKey());
        }
        return map;
    }

    public void setNextSyncFloor(int lastId) {
        if (lastId >= nextSyncId.get()) {
            nextSyncId.set(lastId + 1);
        }
    }

    public int peekNextSyncId() {
        return nextSyncId.get();
    }
}
