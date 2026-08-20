package bin.cnpcplus.smelting;

import bin.cnpcplus.CnpcPlus;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;

/**
 * Server side store of the custom smelting recipes, backed by
 * {CustomNpcs.Dir}/smelting_recipes.dat (compressed NBT, same format as the
 * 1.20.1 / 1.21.1 modules).
 *
 * Unlike those versions this registry is readable on both sides. In 1.12.2 the
 * furnace recipe lookup runs on the client too (there is no recipe sync packet
 * in this version), so the client needs the real list to render progress and to
 * accept fuel in the slot. The list is still only ever *written* by the server
 * and pushed to clients through PacketSmeltingSync; clients never load the file.
 */
public final class SmeltingRecipeRegistry {
    private static final String FILE_NAME = "smelting_recipes.dat";
    private static final int MAX_RECIPES = 256;

    private static final Object LOCK = new Object();
    private static Map<Integer, SmeltingRecipeData> cache;
    private static int nextId = 1;
    private static boolean ready;

    private SmeltingRecipeRegistry() {}

    /**
     * Opens the registry once a world is running.
     *
     * Two things happen strictly before that moment and both must not see custom
     * recipes. FurnaceRecipes.<clinit> runs from Bootstrap inside
     * Minecraft.<init>, long before CustomNpcs exists, so touching the store
     * there dies with NoClassDefFoundError on CustomNpcs.Dir. And every
     * addSmeltingRecipe call - vanilla's own ~70 plus every other mod's - starts
     * by asking getSmeltingResult and drops its recipe as a conflict if the
     * answer is not empty, so answering early would delete the recipes we are
     * supposed to be extending.
     */
    public static void markReady() {
        synchronized (LOCK) {
            ready = true;
        }
    }

    private static File file() {
        File dir = CustomNpcs.Dir != null ? CustomNpcs.Dir : new File("customnpcs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, FILE_NAME);
    }

    private static Map<Integer, SmeltingRecipeData> ensure() {
        synchronized (LOCK) {
            if (!ready) {
                // Before markReady the store must stay invisible: see markReady.
                return java.util.Collections.emptyMap();
            }
            if (cache == null) {
                cache = readDisk();
                int max = 0;
                for (Integer key : cache.keySet()) {
                    if (key != null && key.intValue() > max) {
                        max = key.intValue();
                    }
                }
                nextId = max + 1;
            }
            return cache;
        }
    }

    /**
     * Replaces the whole list without touching the disk file. Used by the client
     * when a sync packet arrives, so client state always mirrors the server.
     */
    public static void acceptSync(List<SmeltingRecipeData> incoming) {
        synchronized (LOCK) {
            Map<Integer, SmeltingRecipeData> map = new LinkedHashMap<Integer, SmeltingRecipeData>();
            if (incoming != null) {
                for (SmeltingRecipeData data : incoming) {
                    if (data == null || data.id < 0 || map.size() >= MAX_RECIPES) {
                        continue;
                    }
                    map.put(Integer.valueOf(data.id), data);
                }
            }
            cache = map;
            // A sync packet only arrives once a world is joined, and it is the
            // client's only source of recipes, so this is its open gate.
            ready = true;
        }
    }

    /** Dropped when a world unloads so a different save never inherits this list. */
    public static void clearCache() {
        synchronized (LOCK) {
            cache = null;
            nextId = 1;
            ready = false;
        }
    }

    public static SmeltingRecipeData get(int id) {
        synchronized (LOCK) {
            SmeltingRecipeData data = ensure().get(Integer.valueOf(id));
            return data == null ? null : data.copy();
        }
    }

    public static List<SmeltingRecipeData> list() {
        synchronized (LOCK) {
            List<SmeltingRecipeData> out = new ArrayList<SmeltingRecipeData>();
            for (SmeltingRecipeData data : ensure().values()) {
                out.add(data.copy());
            }
            return out;
        }
    }

    public static SmeltingRecipeData create(SmeltingRecipeData data) {
        if (data == null) {
            return null;
        }
        synchronized (LOCK) {
            if (!ready) {
                return null;
            }
            Map<Integer, SmeltingRecipeData> map = ensure();
            if (map.size() >= MAX_RECIPES) {
                CnpcPlus.LOGGER.warn("[Smelting] recipe limit {} reached, refusing new recipe", MAX_RECIPES);
                return null;
            }
            data.id = nextId++;
            map.put(Integer.valueOf(data.id), data.copy());
            writeDisk();
            return data;
        }
    }

    public static boolean update(SmeltingRecipeData data) {
        if (data == null || data.id < 0) {
            return false;
        }
        synchronized (LOCK) {
            if (!ready) {
                return false;
            }
            Map<Integer, SmeltingRecipeData> map = ensure();
            // Never silently create on update: an unknown id means a stale client.
            if (!map.containsKey(Integer.valueOf(data.id))) {
                return false;
            }
            map.put(Integer.valueOf(data.id), data.copy());
            writeDisk();
            return true;
        }
    }

    public static boolean remove(int id) {
        synchronized (LOCK) {
            if (!ready) {
                return false;
            }
            if (ensure().remove(Integer.valueOf(id)) == null) {
                return false;
            }
            writeDisk();
            return true;
        }
    }

    private static Map<Integer, SmeltingRecipeData> readDisk() {
        Map<Integer, SmeltingRecipeData> map = new LinkedHashMap<Integer, SmeltingRecipeData>();
        File f = file();
        if (!f.isFile()) {
            return map;
        }
        try {
            NBTTagCompound root;
            InputStream in = new FileInputStream(f);
            try {
                root = CompressedStreamTools.readCompressed(in);
            } finally {
                in.close();
            }
            if (root == null) {
                return map;
            }
            NBTTagList list = root.getTagList("Data", 10);
            for (int i = 0; i < list.tagCount() && map.size() < MAX_RECIPES; ++i) {
                SmeltingRecipeData data = SmeltingRecipeData.fromNBT(list.getCompoundTagAt(i));
                if (data == null || data.id < 0) {
                    continue;
                }
                map.put(Integer.valueOf(data.id), data);
            }
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[Smelting] failed to read " + FILE_NAME, e);
        }
        return map;
    }

    /** Writes to a temp file then renames, so a crash cannot truncate the real one. */
    private static void writeDisk() {
        try {
            NBTTagList list = new NBTTagList();
            for (SmeltingRecipeData data : ensure().values()) {
                list.appendTag(data.toNBT());
            }
            NBTTagCompound root = new NBTTagCompound();
            root.setTag("Data", list);
            root.setInteger("Version", 1);

            File target = file();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(parent, FILE_NAME + ".tmp");
            OutputStream out = new FileOutputStream(tmp);
            try {
                CompressedStreamTools.writeCompressed(root, out);
                out.flush();
            } finally {
                out.close();
            }
            if (target.exists() && !target.delete()) {
                CnpcPlus.LOGGER.warn("[Smelting] cannot replace {}", FILE_NAME);
                return;
            }
            if (!tmp.renameTo(target)) {
                copyFile(tmp, target);
                tmp.delete();
            }
        } catch (Exception e) {
            CnpcPlus.LOGGER.error("[Smelting] failed to write " + FILE_NAME, e);
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        try {
            OutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                }
                out.flush();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
