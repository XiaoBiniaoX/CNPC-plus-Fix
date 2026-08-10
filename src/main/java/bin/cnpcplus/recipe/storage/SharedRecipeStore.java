package bin.cnpcplus.recipe.storage;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.RecipeNbtKeys;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Global single-file shared recipes under customnpcs/shared_recipes.dat.
 * Opt-in per recipe; independent of per-world recipes.dat.
 */
public final class SharedRecipeStore {
    public static final SharedRecipeStore INSTANCE = new SharedRecipeStore();
    public static final String FILE_NAME = "shared_recipes.dat";

    /** nameLower -> recipe snapshot (id used as stable key when present) */
    private final Map<String, RecipeCarpentry> byName = new HashMap<String, RecipeCarpentry>();
    private final Map<Integer, String> nameById = new HashMap<Integer, String>();

    private SharedRecipeStore() {}

    public static File dir() {
        File d = CustomNpcs.Dir;
        if (d == null) d = new File("customnpcs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File file() {
        return new File(dir(), FILE_NAME);
    }

    public synchronized void load() {
        byName.clear();
        nameById.clear();
        File f = file();
        if (!f.exists()) {
            File old = new File(dir(), "shared_recipes.dat_old");
            if (old.exists()) f = old;
            else return;
        }
        try {
            InputStream in = new FileInputStream(f);
            NBTTagCompound root;
            try {
                root = CompressedStreamTools.readCompressed(in);
            } finally {
                in.close();
            }
            if (root == null) return;
            NBTTagList list = root.getTagList("Data", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                RecipeCarpentry r = RecipeCarpentry.read(tag);
                if (r.name == null || r.name.isEmpty()) {
                    r.name = tag.hasKey("Name") ? tag.getString("Name") : ("shared_" + i);
                }
                if (tag.hasKey(RecipeNbtKeys.SYNC_ID)) {
                    r.id = tag.getInteger(RecipeNbtKeys.SYNC_ID);
                } else if (tag.hasKey("ID")) {
                    r.id = tag.getInteger("ID");
                }
                putInternal(r);
            }
            CnpcPlus.LOGGER.info("[SharedRecipeStore] loaded {}", Integer.valueOf(byName.size()));
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[SharedRecipeStore] load failed", e);
        }
    }

    public synchronized void save() {
        try {
            NBTTagList list = new NBTTagList();
            for (RecipeCarpentry r : byName.values()) {
                if (r == null || !r.savesRecipe) continue;
                NBTTagCompound tag = r.writeNBT();
                if (r.name != null) tag.setString("Name", r.name);
                if (r.id > 0) {
                    tag.setInteger(RecipeNbtKeys.SYNC_ID, r.id);
                    tag.setInteger("ID", r.id);
                }
                list.appendTag(tag);
            }
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("Data", list);
            root.setInteger("Version", 1);

            File saveDir = dir();
            File fileNew = new File(saveDir, "shared_recipes.dat_new");
            File fileOld = new File(saveDir, "shared_recipes.dat_old");
            File file = new File(saveDir, FILE_NAME);
            OutputStream out = new FileOutputStream(fileNew);
            try {
                CompressedStreamTools.writeCompressed(root, out);
                out.flush();
            } finally {
                out.close();
            }
            if (fileOld.exists()) fileOld.delete();
            if (file.exists() && !file.renameTo(fileOld)) file.delete();
            if (!fileNew.renameTo(file)) {
                copy(fileNew, file);
                fileNew.delete();
            }
            if (fileNew.exists()) fileNew.delete();
            CnpcPlus.LOGGER.info("[SharedRecipeStore] saved {}", Integer.valueOf(list.tagCount()));
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[SharedRecipeStore] save failed", e);
        }
    }

    private static void copy(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                out.flush();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private void putInternal(RecipeCarpentry r) {
        if (r == null || r.name == null || r.name.isEmpty()) return;
        String key = r.name.toLowerCase(Locale.ROOT);
        RecipeCarpentry prev = byName.put(key, r);
        if (prev != null && prev.id > 0) nameById.remove(Integer.valueOf(prev.id));
        if (r.id > 0) nameById.put(Integer.valueOf(r.id), key);
    }

    /** Persist a live recipe snapshot into shared file. */
    public synchronized boolean persist(RecipeCarpentry live) {
        if (live == null || live.name == null || live.name.isEmpty()) return false;
        NBTTagCompound tag = live.writeNBT();
        tag.setString("Name", live.name);
        if (live.id > 0) {
            tag.setInteger(RecipeNbtKeys.SYNC_ID, live.id);
            tag.setInteger("ID", live.id);
        }
        RecipeCarpentry copy = RecipeCarpentry.read(tag);
        copy.name = live.name;
        copy.id = live.id;
        copy.savesRecipe = true;
        putInternal(copy);
        save();
        return true;
    }

    /** Remove from shared file only; does not touch world memory. */
    public synchronized boolean unpersist(int syncId, String name) {
        String key = null;
        if (syncId > 0) key = nameById.get(Integer.valueOf(syncId));
        if (key == null && name != null) key = name.toLowerCase(Locale.ROOT);
        if (key == null) return false;
        RecipeCarpentry removed = byName.remove(key);
        if (removed == null) return false;
        if (removed.id > 0) nameById.remove(Integer.valueOf(removed.id));
        save();
        return true;
    }

    public synchronized boolean isPersisted(int syncId, String name) {
        if (syncId > 0 && nameById.containsKey(Integer.valueOf(syncId))) return true;
        if (name != null && byName.containsKey(name.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    public synchronized List<RecipeCarpentry> all() {
        return new ArrayList<RecipeCarpentry>(byName.values());
    }

    public synchronized Map<String, RecipeCarpentry> snapshot() {
        return Collections.unmodifiableMap(new HashMap<String, RecipeCarpentry>(byName));
    }
}
