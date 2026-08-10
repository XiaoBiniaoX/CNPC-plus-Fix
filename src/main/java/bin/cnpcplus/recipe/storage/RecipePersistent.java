package bin.cnpcplus.recipe.storage;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.id.RecipeIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cross-world single-file store at CustomNpcs.Dir/recipes_persistent.dat.
 * Only recipes the player explicitly "persists". Unpersist does not touch world recipes.dat.
 */
public final class RecipePersistent {
    public static final RecipePersistent INSTANCE = new RecipePersistent();
    private static final String FILE = "recipes_persistent.dat";
    private static final String NBT_SYNC = "CnpcPlusSyncId";
    private static final String NBT_KEY = "CnpcPlusPersistKey";

    /** key -> full recipe nbt (includes Name + body) */
    private final Map<String, CompoundTag> entries = new HashMap<>();

    private RecipePersistent() {}

    public File file() {
        File dir = CustomNpcs.Dir;
        if (dir == null) return null;
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, FILE);
    }

    public void reloadFromDisk() {
        entries.clear();
        File f = file();
        if (f == null || !f.exists()) return;
        try (InputStream in = new FileInputStream(f)) {
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            ListTag list = root.getList("Data", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                String key = tag.contains(NBT_KEY) ? tag.getString(NBT_KEY) : keyOf(tag);
                if (key == null || key.isEmpty()) continue;
                entries.put(key, tag.copy());
            }
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[RecipePersistent] load failed", e);
        }
    }

    private void saveToDisk() {
        File f = file();
        if (f == null) {
            CnpcPlus.LOGGER.warn("[RecipePersistent] cannot save: Dir null");
            return;
        }
        try {
            ListTag list = new ListTag();
            for (Map.Entry<String, CompoundTag> e : entries.entrySet()) {
                CompoundTag tag = e.getValue().copy();
                tag.putString(NBT_KEY, e.getKey());
                list.add(tag);
            }
            CompoundTag root = new CompoundTag();
            root.put("Data", list);
            root.putInt("Version", 1);
            File tmp = new File(f.getParentFile(), FILE + "_new");
            try (OutputStream out = new FileOutputStream(tmp)) {
                NbtIo.writeCompressed(root, out);
                out.flush();
            }
            if (f.exists() && !f.delete()) {
                CnpcPlus.LOGGER.warn("[RecipePersistent] could not delete old file");
            }
            if (!tmp.renameTo(f)) {
                try (InputStream in = new FileInputStream(tmp); OutputStream out = new FileOutputStream(f)) {
                    in.transferTo(out);
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[RecipePersistent] save failed", e);
        }
    }

    public static String keyOf(RecipeCarpentry recipe) {
        if (recipe == null) return null;
        String name = recipe.name != null ? recipe.name : "unnamed";
        return (recipe.isGlobal ? "g:" : "a:") + name.toLowerCase(Locale.ROOT);
    }

    private static String keyOf(CompoundTag tag) {
        String name = tag.getString("Name");
        if (name == null || name.isEmpty()) name = "unnamed";
        boolean global = tag.contains("Global") && tag.getBoolean("Global");
        return (global ? "g:" : "a:") + name.toLowerCase(Locale.ROOT);
    }

    public boolean isPersisted(RecipeCarpentry recipe) {
        String key = keyOf(recipe);
        return key != null && isPersistedKey(key);
    }

    public boolean isPersistedKey(String key) {
        if (key == null) return false;
        if (entries.isEmpty()) reloadFromDisk();
        return entries.containsKey(key);
    }

    public boolean isPersistedName(String name, boolean global) {
        if (name == null) return false;
        if (entries.isEmpty()) reloadFromDisk();
        return entries.containsKey((global ? "g:" : "a:") + name.toLowerCase(Locale.ROOT));
    }

    /** Add/update one recipe in persistent file; also ensures it exists in current world controller. */
    public void persist(RecipeCarpentry recipe, HolderLookup.Provider provider, RecipeController controller) {
        if (recipe == null || provider == null) return;
        reloadFromDisk();
        String key = keyOf(recipe);
        CompoundTag tag = RecipeStorage.INSTANCE.writeRecipeTag(recipe, provider);
        tag.putString(NBT_KEY, key);
        // ensure Global flag present for reload identity
        tag.putBoolean("Global", recipe.isGlobal);
        entries.put(key, tag);
        saveToDisk();

        // ensure in current world maps + world disk
        putIntoController(recipe, controller);
        if (CustomNpcs.Server != null) {
            RecipeStorage.INSTANCE.saveAll(provider, controller);
        }
    }

    /** Remove from persistent file only. World recipes.dat untouched. */
    public void unpersist(RecipeCarpentry recipe) {
        if (recipe == null) return;
        reloadFromDisk();
        String key = keyOf(recipe);
        if (entries.remove(key) != null) {
            saveToDisk();
        }
    }

    public void unpersistByKey(String key) {
        if (key == null) return;
        reloadFromDisk();
        if (entries.remove(key) != null) {
            saveToDisk();
        }
    }

    /** After world recipes loaded: inject missing persistent recipes into memory (+ world save). */
    public void mergeInto(HolderLookup.Provider provider, RecipeController controller) {
        reloadFromDisk();
        if (entries.isEmpty() || controller == null || provider == null) return;
        int added = 0;
        for (CompoundTag tag : entries.values()) {
            try {
                RecipeCarpentry recipe = RecipeCarpentry.load(tag, provider);
                recipe.name = tag.getString("Name");
                if (recipe.name == null || recipe.name.isEmpty()) continue;
                if (tag.contains("Global")) recipe.isGlobal = tag.getBoolean("Global");
                recipe.savesRecipe = true;

                // if same name already in this world, keep world copy (don't overwrite local edits)
                Integer existing = RecipeIds.INSTANCE.syncIdByName(recipe.name);
                if (existing != null) {
                    RecipeCarpentry cur = RecipeIds.INSTANCE.bySyncId(existing);
                    if (cur != null && cur.isGlobal == recipe.isGlobal) {
                        continue;
                    }
                }

                int preferred = tag.contains(NBT_SYNC) ? tag.getInt(NBT_SYNC) : -1;
                // avoid sync id collision with different recipe
                if (preferred > 0 && RecipeIds.INSTANCE.bySyncId(preferred) != null) {
                    preferred = -1;
                }
                int syncId = RecipeIds.INSTANCE.register(recipe, preferred);
                ResourceLocation loc = RecipeIds.INSTANCE.locationOf(syncId);
                if (recipe.isGlobal) {
                    controller.globalRecipes.put(loc, recipe);
                } else {
                    controller.anvilRecipes.put(loc, recipe);
                }
                controller.nextId = Math.max(controller.nextId, RecipeIds.INSTANCE.peekNextSyncId());
                added++;
            } catch (Exception e) {
                CnpcPlus.LOGGER.error("[RecipePersistent] merge entry failed", e);
            }
        }
        if (added > 0) {
            RecipeStorage.INSTANCE.saveAll(provider, controller);
        }
    }

    private void putIntoController(RecipeCarpentry recipe, RecipeController controller) {
        if (controller == null || recipe == null) return;
        Integer syncId = RecipeIds.INSTANCE.syncIdOfRecipe(recipe);
        if (syncId == null) {
            syncId = RecipeIds.INSTANCE.register(recipe);
        }
        ResourceLocation loc = RecipeIds.INSTANCE.locationOf(syncId);
        controller.globalRecipes.remove(loc);
        controller.anvilRecipes.remove(loc);
        if (recipe.isGlobal) {
            controller.globalRecipes.put(loc, recipe);
        } else {
            controller.anvilRecipes.put(loc, recipe);
        }
    }
}
