package top.cnpcplus.smithing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtAccounter;
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
 * 自定义锻造配方注册中心 / 生命周期管理器（独立于 Minecraft RecipeManager，227 从 SmeltingRecipeRegistry 复制）。
 * - 数据源 = 磁盘文件（cnpcplus 锻造配方库），服务端权威。
 * - 负责 ID 分配、新建/删除/修改、reload 后恢复。
 * - Minecraft RecipeManager 只是最终呈现层（由 RecipeManager 注入层读取本 Registry 动态注册/移除）。
 */
public final class SmithingRecipeRegistry {

    private static final String FILE_NAME = "smithing_recipes.dat";
    /** 缓存与写盘的共享锁：单人模式下客户端线程与内置服务端线程会同时访问这些静态字段。 */
    private static final Object LOCK = new Object();
    private static Map<Integer, SmithingRecipeData> cache;
    private static int nextId = 1;

    /**
     * 安全异步落盘（228 用户授权）：单线程串行（防连续保存乱序覆盖）。
     * 红线：本 executor 内只允许纯数据与 IO —— NbtIo 序列化、File 替换；
     * NBT 构造（含 ItemStack.save，可能触碰 Forge caps）与一切 net.minecraft 实例
     * （RecipeManager/Level/ServerPlayer 等）必须留在主线程，严禁投入此线程。
     * 由此主线程的保存路径只做「数据入缓存 + 构造 NBT 根节点 + 提交任务」，磁盘阻塞不再卡服。
     */
    private static final java.util.concurrent.ExecutorService DISK_EXEC =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "cnpcplus-smithing-disk");
                t.setDaemon(true);
                return t;
            });

    private SmithingRecipeRegistry() {}

    public static File file() {
        File dir = CustomNpcs.Dir != null ? CustomNpcs.Dir : new File("customnpcs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    /**
     * 本注册表是服务端权威数据，只有服务端线程可以读盘。
     * 客户端（含连到远程服务器的客户端）一律拿空表，配方列表由 PacketSmithingSync 下发到 SmithingClientData。
     * 否则连服时客户端会去读自己本地的 smithing_recipes.dat，导致显示与服务端不一致。
     */
    private static boolean hasServer() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null;
    }

    private static Map<Integer, SmithingRecipeData> ensure() {
        synchronized (LOCK) {
            if (cache == null) {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server == null) return new LinkedHashMap<>();
                cache = readDisk(server.registryAccess());
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

    private static Map<Integer, SmithingRecipeData> readDisk(HolderLookup.Provider registries) {
        Map<Integer, SmithingRecipeData> map = new LinkedHashMap<>();
        File f = file();
        if (!f.isFile()) return map;
        try (FileInputStream in = new FileInputStream(f)) {
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            if (root == null) return map;
            ListTag list = root.getList("Data", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                SmithingRecipeData d = SmithingRecipeData.fromNBT(registries, list.getCompound(i));
                if (d == null || d.id < 0) continue;
                map.put(d.id, d);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("读取自定义锻造配方文件失败", e);
        }
        return map;
    }

    /**
     * 落盘（调用方已在 LOCK 内、主线程）：NBT 根节点在主线程构造（数据快照，纯 NBT 对象），
     * 序列化+文件替换提交给安全异步单线程执行（纯 IO）。单线程保证多次快速保存按序写入。
     */
    private static void writeDisk(HolderLookup.Provider registries) {
        try {
            CompoundTag root = buildRoot(registries);
            File target = file();
            DISK_EXEC.execute(() -> writeRoot(root, target));
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("写入自定义锻造配方文件失败（构造阶段）", e);
        }
    }

    /** 主线程：从缓存构造 NBT 根（含 ItemStack.save，属纯数据但可能触碰 Forge caps —— 留主线程）。 */
    private static CompoundTag buildRoot(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (SmithingRecipeData d : ensure().values()) {
            list.add(d.toNBT(registries));
        }
        CompoundTag root = new CompoundTag();
        root.put("Data", list);
        return root;
    }

    /** 安全异步（cnpcplus-smithing-disk 线程）：只做 NbtIo 序列化与文件原子替换，不碰任何 MC 实例。 */
    private static void writeRoot(CompoundTag root, File f) {
        try {
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
            LoggerFactory.getLogger("cnpcplus").error("写入自定义锻造配方文件失败（异步写阶段）", e);
        }
    }

    /**
     * 等待异步落盘队列排空（关服时调用，阻塞主线程等 Future —— 只是等待纯 IO 任务，不碰 MC 实例）。
     * 必须在 clearCache 之前跑：否则最后一批修改的 root 快照还没写完就被清掉窗口期数据。
     */
    public static void awaitDiskPending() {
        try {
            DISK_EXEC.submit(() -> {}).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("等待锻造配方异步落盘超时/失败", e);
        }
    }

    /** 新建：分配 id 并保存，返回带 id 的数据。 */
    public static SmithingRecipeData create(HolderLookup.Provider registries, SmithingRecipeData data) {
        if (data == null) return null;
        synchronized (LOCK) {
            Map<Integer, SmithingRecipeData> map = ensure();
            data.id = nextId++;
            map.put(data.id, data.copy());
            writeDisk(registries);
            return data;
        }
    }

    /** 修改已存在的配方；id 不存在则忽略。 */
    public static boolean update(HolderLookup.Provider registries, SmithingRecipeData data) {
        if (data == null || data.id < 0) return false;
        synchronized (LOCK) {
            Map<Integer, SmithingRecipeData> map = ensure();
            if (!map.containsKey(data.id)) {
                LoggerFactory.getLogger("cnpcplus")
                        .debug("[smithing] Registry更新失败(不存在) id={}", data.id);
                return false;
            }
            map.put(data.id, data.copy());
            writeDisk(registries);
            return true;
        }
    }

    public static boolean remove(HolderLookup.Provider registries, int id) {
        synchronized (LOCK) {
            Map<Integer, SmithingRecipeData> map = ensure();
            if (map.remove(id) == null) {
                LoggerFactory.getLogger("cnpcplus")
                        .debug("[smithing] Registry删除失败(不存在) id={}", id);
                return false;
            }
            writeDisk(registries);
            return true;
        }
    }

    /** 取配方（返回副本，防止调用方改到缓存里的对象）。客户端一律取不到，见 hasServer()。 */
    public static SmithingRecipeData get(int id) {
        if (!hasServer()) return null;
        synchronized (LOCK) {
            SmithingRecipeData d = ensure().get(id);
            return d == null ? null : d.copy();
        }
    }

    /** 全部配方（副本列表）。客户端一律返回空表，配方由服务端下发到 SmithingClientData。 */
    public static List<SmithingRecipeData> list() {
        if (!hasServer()) return new ArrayList<>();
        synchronized (LOCK) {
            List<SmithingRecipeData> out = new ArrayList<>(ensure().size());
            for (SmithingRecipeData d : ensure().values()) out.add(d.copy());
            return out;
        }
    }
}
