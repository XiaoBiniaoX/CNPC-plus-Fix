package top.cnpcplus.smelting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import noppes.npcs.CustomNpcs;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义熔炼配方注册中心 / 生命周期管理器（独立于 Minecraft RecipeManager）。
 * - 数据源 = 磁盘文件（cnpcplus 熔炼配方库），服务端权威。
 * - 负责 ID 分配、新建/删除/修改/重命名、reload 后恢复。
 * - Minecraft RecipeManager 只是最终呈现层（由 RecipeManager 注入层读取本 Registry 动态注册/移除）。
 */
public final class SmeltingRecipeRegistry {

    private static final String FILE_NAME = "smelting_recipes.dat";
    /** 缓存与写盘的共享锁：单人模式下客户端线程与内置服务端线程会同时访问这些静态字段。 */
    private static final Object LOCK = new Object();
    private static Map<Integer, SmeltingRecipeData> cache;
    private static int nextId = 1;

    private SmeltingRecipeRegistry() {}

    public static File file() {
        File dir = CustomNpcs.Dir != null ? CustomNpcs.Dir : new File("customnpcs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    /**
     * 本注册表是服务端权威数据，只有服务端线程可以读盘。
     * 客户端（含连到远程服务器的客户端）一律拿空表，配方列表由 PacketSmeltingSync 下发到 SmeltingClientData。
     * 否则连服时客户端会去读自己本地的 smelting_recipes.dat，导致显示与服务端不一致。
     */
    private static boolean hasServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null;
    }

    private static Map<Integer, SmeltingRecipeData> ensure() {
        synchronized (LOCK) {
            if (cache == null) {
                cache = readDisk();
                nextId = cache.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
            }
            return cache;
        }
    }

    /** 服务端停止时清空缓存：单人切换存档后必须重读，否则会把上个存档的配方带进新世界。 */
    public static void clearCache() {
        synchronized (LOCK) {
            cache = null;
            nextId = 1;
        }
    }

    private static Map<Integer, SmeltingRecipeData> readDisk() {
        Map<Integer, SmeltingRecipeData> map = new LinkedHashMap<>();
        File f = file();
        if (!f.isFile()) return map;
        try (FileInputStream in = new FileInputStream(f)) {
            CompoundTag root = NbtIo.readCompressed(in);
            if (root == null) return map;
            ListTag list = root.getList("Data", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                SmeltingRecipeData d = SmeltingRecipeData.fromNBT(list.getCompound(i));
                if (d == null || d.id < 0) continue;
                map.put(d.id, d);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("读取自定义熔炼配方文件失败", e);
        }
        return map;
    }

    private static void writeDisk() {
        try {
            ListTag list = new ListTag();
            for (SmeltingRecipeData d : ensure().values()) {
                list.add(d.toNBT());
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
            if (f.exists() && !f.delete()) return;
            if (!tmp.renameTo(f)) {
                try (FileInputStream in = new FileInputStream(tmp); FileOutputStream out = new FileOutputStream(f)) {
                    in.transferTo(out);
                }
                tmp.delete();
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("写入自定义熔炼配方文件失败", e);
        }
    }

    /** 新建：分配 id 并保存，返回带 id 的数据。 */
    public static SmeltingRecipeData create(SmeltingRecipeData data) {
        if (data == null) return null;
        synchronized (LOCK) {
            Map<Integer, SmeltingRecipeData> map = ensure();
            data.id = nextId++;
            map.put(data.id, data.copy());
            writeDisk();
            return data;
        }
    }

    /** 修改已存在的配方；id 不存在则忽略。 */
    public static boolean update(SmeltingRecipeData data) {
        if (data == null || data.id < 0) return false;
        synchronized (LOCK) {
            Map<Integer, SmeltingRecipeData> map = ensure();
            if (!map.containsKey(data.id)) return false;
            map.put(data.id, data.copy());
            writeDisk();
            return true;
        }
    }

    public static boolean remove(int id) {
        synchronized (LOCK) {
            Map<Integer, SmeltingRecipeData> map = ensure();
            if (map.remove(id) == null) return false;
            writeDisk();
            return true;
        }
    }

    /** 取配方（返回副本，防止调用方改到缓存里的对象）。客户端一律取不到，见 hasServer()。 */
    public static SmeltingRecipeData get(int id) {
        if (!hasServer()) return null;
        synchronized (LOCK) {
            SmeltingRecipeData d = ensure().get(id);
            return d == null ? null : d.copy();
        }
    }

    /** 全部配方（副本列表）。客户端一律返回空表，配方由服务端下发到 SmeltingClientData。 */
    public static List<SmeltingRecipeData> list() {
        if (!hasServer()) return new ArrayList<>();
        synchronized (LOCK) {
            List<SmeltingRecipeData> out = new ArrayList<>(ensure().size());
            for (SmeltingRecipeData d : ensure().values()) out.add(d.copy());
            return out;
        }
    }
}
