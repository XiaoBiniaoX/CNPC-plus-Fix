package top.cnpcplus.persist;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.data.RecipeCarpentry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opt-in multi-recipe persistence: game-root customnpcs/persisted_recipes.dat */
public final class PersistedRecipeStore {

    private static final String FILE_NAME = "persisted_recipes.dat";
    private static Map<ResourceLocation, RecipeCarpentry> cache;
    /** Client-only mirror of persisted ids for GUI button state. */
    private static final java.util.HashSet<ResourceLocation> clientIds = new java.util.HashSet<>();

    private PersistedRecipeStore() {}

    public static File file() {
        File dir = CustomNpcs.Dir != null ? CustomNpcs.Dir : new File("customnpcs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    private static Map<ResourceLocation, RecipeCarpentry> ensure() {
        if (cache == null) cache = readDisk();
        return cache;
    }

    private static Map<ResourceLocation, RecipeCarpentry> readDisk() {
        Map<ResourceLocation, RecipeCarpentry> map = new LinkedHashMap<>();
        File f = file();
        if (!f.isFile()) return map;
        try (FileInputStream in = new FileInputStream(f)) {
            CompoundTag root = NbtIo.readCompressed(in);
            if (root == null) return map;
            ListTag list = root.getList("Data", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                RecipeCarpentry recipe = RecipeCarpentry.load(list.getCompound(i));
                if (recipe == null || recipe.getId() == null) continue;
                map.put(recipe.getId(), recipe);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("读取持久化配方文件失败", e);
        }
        return map;
    }

    private static void writeDisk(Map<ResourceLocation, RecipeCarpentry> map) {
        try {
            ListTag list = new ListTag();
            for (RecipeCarpentry recipe : map.values()) {
                if (recipe == null || recipe.getId() == null) continue;
                list.add(recipe.writeNBT());
            }
            CompoundTag root = new CompoundTag();
            root.put("Data", list);
            File f = file();
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tmp = new File(f.getParentFile(), FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                NbtIo.writeCompressed(root, out);
            }
            if (f.exists() && !f.delete()) {
                // keep tmp if replace fails
                return;
            }
            if (!tmp.renameTo(f)) {
                // fallback copy
                try (FileInputStream in = new FileInputStream(tmp); FileOutputStream out = new FileOutputStream(f)) {
                    in.transferTo(out);
                }
                tmp.delete();
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("写入持久化配方文件失败", e);
        }
    }

    public static void put(RecipeCarpentry recipe) {
        if (recipe == null || recipe.getId() == null) return;
        Map<ResourceLocation, RecipeCarpentry> map = ensure();
        map.put(recipe.getId(), recipe);
        writeDisk(map);
    }

    public static void remove(ResourceLocation id) {
        if (id == null) return;
        Map<ResourceLocation, RecipeCarpentry> map = ensure();
        if (map.remove(id) != null) writeDisk(map);
    }

    public static boolean contains(ResourceLocation id) {
        return id != null && ensure().containsKey(id);
    }

    public static List<RecipeCarpentry> list() {
        return new ArrayList<>(ensure().values());
    }

    public static Set<ResourceLocation> ids() {
        return Collections.unmodifiableSet(ensure().keySet());
    }

    public static void clientSetAll(List<ResourceLocation> ids) {
        clientIds.clear();
        clientIds.addAll(ids);
    }

    public static void clientSet(ResourceLocation id, boolean persisted) {
        if (id == null) return;
        if (persisted) clientIds.add(id);
        else clientIds.remove(id);
    }

    public static boolean clientContains(ResourceLocation id) {
        return id != null && clientIds.contains(id);
    }
}
