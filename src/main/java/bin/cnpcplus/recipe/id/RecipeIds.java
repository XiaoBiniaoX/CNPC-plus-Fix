package bin.cnpcplus.recipe.id;

import bin.cnpcplus.CnpcPlus;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Identity: syncId (stored as recipe.id). Stable across renames.
 */
public final class RecipeIds {
    public static final RecipeIds INSTANCE = new RecipeIds();

    private final Map<Integer, RecipeCarpentry> bySyncRecipe = new HashMap<Integer, RecipeCarpentry>();
    private final Map<String, Integer> byNameLower = new HashMap<String, Integer>();
    private final AtomicInteger nextSyncId = new AtomicInteger(1);

    private RecipeIds() {}

    public void clear() {
        bySyncRecipe.clear();
        byNameLower.clear();
        nextSyncId.set(1);
        CnpcPlus.LOGGER.debug("[RecipeIds] cleared");
    }

    public Integer syncIdOfRecipe(RecipeCarpentry recipe) {
        if (recipe == null) return null;
        if (recipe.id > 0 && bySyncRecipe.get(Integer.valueOf(recipe.id)) == recipe) {
            return Integer.valueOf(recipe.id);
        }
        for (Map.Entry<Integer, RecipeCarpentry> e : bySyncRecipe.entrySet()) {
            if (e.getValue() == recipe) return e.getKey();
        }
        return null;
    }

    public int register(RecipeCarpentry recipe) {
        Integer existing = syncIdOfRecipe(recipe);
        if (existing != null) {
            return rebind(existing.intValue(), recipe);
        }
        if (recipe.id > 0) {
            return rebind(recipe.id, recipe);
        }
        if (recipe.name != null) {
            Integer byName = byNameLower.get(recipe.name.toLowerCase(Locale.ROOT));
            if (byName != null) {
                return rebind(byName.intValue(), recipe);
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
        RecipeCarpentry prev = bySyncRecipe.get(Integer.valueOf(syncId));
        if (prev != null && prev.name != null) {
            byNameLower.remove(prev.name.toLowerCase(Locale.ROOT));
        }
        recipe.id = syncId;
        bySyncRecipe.put(Integer.valueOf(syncId), recipe);
        if (recipe.name != null) {
            byNameLower.put(recipe.name.toLowerCase(Locale.ROOT), Integer.valueOf(syncId));
        }
        if (syncId >= nextSyncId.get()) {
            nextSyncId.set(syncId + 1);
        }
        return syncId;
    }

    public void unregister(int syncId) {
        RecipeCarpentry r = bySyncRecipe.remove(Integer.valueOf(syncId));
        if (r != null && r.name != null) {
            byNameLower.remove(r.name.toLowerCase(Locale.ROOT));
        }
    }

    public RecipeCarpentry bySyncId(int syncId) {
        return bySyncRecipe.get(Integer.valueOf(syncId));
    }

    public Integer syncIdByName(String name) {
        if (name == null) return null;
        return byNameLower.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, Integer> scrollMapAnvil() {
        return scrollMap(false);
    }

    public Map<String, Integer> scrollMapGlobal() {
        return scrollMap(true);
    }

    private Map<String, Integer> scrollMap(boolean global) {
        Map<String, Integer> map = new HashMap<String, Integer>();
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
