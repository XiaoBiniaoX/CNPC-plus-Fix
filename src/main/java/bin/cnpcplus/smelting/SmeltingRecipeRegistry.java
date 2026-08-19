package bin.cnpcplus.smelting;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import noppes.npcs.CustomNpcs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SmeltingRecipeRegistry {
    private static final Object LOCK = new Object();
    private static Map<Integer, SmeltingRecipeData> entries;
    private static int nextId = 1;
    private SmeltingRecipeRegistry() {}

    private static File file() {
        File dir = CustomNpcs.Dir == null ? new File("customnpcs") : CustomNpcs.Dir;
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "smelting_recipes.dat");
    }

    private static Map<Integer, SmeltingRecipeData> entries(HolderLookup.Provider registries) {
        if (entries != null) return entries;
        entries = new LinkedHashMap<>();
        File file = file();
        if (file.isFile()) {
            try (FileInputStream in = new FileInputStream(file)) {
                ListTag list = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.create(4_194_304L)).getList("Data", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(list.size(), 256); i++) {
                    SmeltingRecipeData data = SmeltingRecipeData.load(registries, list.getCompound(i));
                    if (data.id >= 0 && !data.input.isEmpty() && !data.output.isEmpty()) entries.put(data.id, data);
                }
            } catch (Exception ex) {
                CnpcPlus.LOGGER.error("Unable to read smelting recipes", ex);
            }
        }
        nextId = entries.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        return entries;
    }

    private static void write(HolderLookup.Provider registries) {
        try {
            CompoundTag root = new CompoundTag();
            ListTag list = new ListTag();
            for (SmeltingRecipeData data : entries.values()) list.add(data.save(registries));
            root.put("Data", list);
            File tmp = new File(file().getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) { NbtIo.writeCompressed(root, out); }
            File target = file();
            if (target.exists()) target.delete();
            tmp.renameTo(target);
        } catch (Exception ex) { CnpcPlus.LOGGER.error("Unable to write smelting recipes", ex); }
    }

    public static void clear() { synchronized (LOCK) { entries = null; nextId = 1; } }
    public static List<SmeltingRecipeData> list(HolderLookup.Provider r) { synchronized (LOCK) { return entries(r).values().stream().map(d -> d.copy(r)).toList(); } }
    public static SmeltingRecipeData get(HolderLookup.Provider r, int id) { synchronized (LOCK) { var d = entries(r).get(id); return d == null ? null : d.copy(r); } }
    public static SmeltingRecipeData create(HolderLookup.Provider r, SmeltingRecipeData data) { synchronized (LOCK) { data.id = nextId++; entries(r).put(data.id, data.copy(r)); write(r); return data.copy(r); } }
    public static boolean update(HolderLookup.Provider r, SmeltingRecipeData data) { synchronized (LOCK) { if (!entries(r).containsKey(data.id)) return false; entries(r).put(data.id, data.copy(r)); write(r); return true; } }
    public static boolean remove(HolderLookup.Provider r, int id) { synchronized (LOCK) { if (entries(r).remove(id) == null) return false; write(r); return true; } }
}
