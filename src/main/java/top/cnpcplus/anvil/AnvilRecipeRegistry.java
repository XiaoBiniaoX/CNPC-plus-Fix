package top.cnpcplus.anvil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.CustomNpcs;
import org.slf4j.LoggerFactory;
import top.cnpcplus.anvil.network.AnvilPacketHandler;
import top.cnpcplus.anvil.network.PacketAnvilSync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义铁砧配方注册中心 / 生命周期管理器（阶段231 从 SmithingRecipeRegistry 复制改名）。
 * - 数据源 = 磁盘文件（cnpcplus 铁砧配方库），服务端权威。
 * - 与熔炼/锻造的本质差异：**不注入 Minecraft RecipeManager**（铁砧 vanilla AnvilMenu 不查
 *   RecipeManager，反编译实证）——配方由 AnvilCraftHandler 在 Forge AnvilUpdateEvent 直查本表生效，
 *   客户端编辑列表由 PacketAnvilSync 下发到 AnvilClientData。
 * - 一人创建全服可用 = CRUD 后 syncToPlayers 全员广播（事件在服务端菜单实例查本表）。
 */
public final class AnvilRecipeRegistry {

    private static final String FILE_NAME = "anvil_recipes.dat";
    /** 缓存与写盘的共享锁：单人模式下客户端线程与内置服务端线程会同时访问这些静态字段。 */
    private static final Object LOCK = new Object();
    private static Map<Integer, AnvilRecipeData> cache;
    private static int nextId = 1;

    /**
     * 安全异步落盘（228 用户授权，红线同锻造）：单线程串行（防连续保存乱序覆盖）。
     * 本 executor 内只允许纯数据与 IO —— NbtIo 序列化、File 替换；
     * NBT 构造（含 ItemStack.save，可能触碰 Forge caps）与一切 net.minecraft 实例
     * 必须留在主线程，严禁投入此线程。
     */
    private static final java.util.concurrent.ExecutorService DISK_EXEC =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "cnpcplus-anvil-disk");
                t.setDaemon(true);
                return t;
            });

    private AnvilRecipeRegistry() {}

    public static File file() {
        File dir = CustomNpcs.Dir != null ? CustomNpcs.Dir : new File("customnpcs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, FILE_NAME);
    }

    /**
     * 本注册表是服务端权威数据，只有服务端线程可以读盘。
     * 客户端（含连到远程服务器的客户端）一律拿空表，配方列表由 PacketAnvilSync 下发到 AnvilClientData。
     * 否则连服时客户端会去读自己本地的 anvil_recipes.dat，导致显示与服务端不一致。
     */
    private static boolean hasServer() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null;
    }

    private static Map<Integer, AnvilRecipeData> ensure() {
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

    private static Map<Integer, AnvilRecipeData> readDisk() {
        Map<Integer, AnvilRecipeData> map = new LinkedHashMap<>();
        File f = file();
        if (!f.isFile()) return map;
        try (FileInputStream in = new FileInputStream(f)) {
            CompoundTag root = NbtIo.readCompressed(in);
            if (root == null) return map;
            ListTag list = root.getList("Data", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                AnvilRecipeData d = AnvilRecipeData.fromNBT(list.getCompound(i));
                if (d == null || d.id < 0) continue;
                map.put(d.id, d);
            }
            // 233d-UI9 日志清理：info→debug（默认不输出；只留 error 级，排查时临时开 debug 即可）
            LoggerFactory.getLogger("cnpcplus").debug("[anvil] 载入自定义铁砧配方 {} 条", map.size());
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("读取自定义铁砧配方文件失败", e);
        }
        return map;
    }

    /**
     * 落盘（调用方已在 LOCK 内、主线程）：NBT 根节点在主线程构造（数据快照，纯 NBT 对象），
     * 序列化+文件替换提交给安全异步单线程执行（纯 IO）。单线程保证多次快速保存按序写入。
     */
    private static void writeDisk() {
        try {
            CompoundTag root = buildRoot();
            DISK_EXEC.execute(() -> writeRoot(root));
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("写入自定义铁砧配方文件失败（构造阶段）", e);
        }
    }

    /** 主线程：从缓存构造 NBT 根（含 ItemStack.save，属纯数据但可能触碰 Forge caps —— 留主线程）。 */
    private static CompoundTag buildRoot() {
        ListTag list = new ListTag();
        for (AnvilRecipeData d : ensure().values()) {
            list.add(d.toNBT());
        }
        CompoundTag root = new CompoundTag();
        root.put("Data", list);
        return root;
    }

    /** 安全异步（cnpcplus-anvil-disk 线程）：只做 NbtIo 序列化与文件原子替换，不碰任何 MC 实例。 */
    private static void writeRoot(CompoundTag root) {
        try {
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
            LoggerFactory.getLogger("cnpcplus").error("写入自定义铁砧配方文件失败（异步写阶段）", e);
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
            LoggerFactory.getLogger("cnpcplus").error("等待铁砧配方异步落盘超时/失败", e);
        }
    }

    /** 新建：分配 id 并保存，返回带 id 的数据。 */
    public static AnvilRecipeData create(AnvilRecipeData data) {
        if (data == null) return null;
        synchronized (LOCK) {
            Map<Integer, AnvilRecipeData> map = ensure();
            data.id = nextId++;
            map.put(data.id, data.copy());
            writeDisk();
            return data;
        }
    }

    /** 修改已存在的配方；id 不存在则忽略。 */
    public static boolean update(AnvilRecipeData data) {
        if (data == null || data.id < 0) return false;
        synchronized (LOCK) {
            Map<Integer, AnvilRecipeData> map = ensure();
            if (!map.containsKey(data.id)) {
                LoggerFactory.getLogger("cnpcplus")
                        .debug("[anvil] Registry更新失败(不存在) id={}", data.id);
                return false;
            }
            map.put(data.id, data.copy());
            writeDisk();
            return true;
        }
    }

    public static boolean remove(int id) {
        synchronized (LOCK) {
            Map<Integer, AnvilRecipeData> map = ensure();
            if (map.remove(id) == null) {
                LoggerFactory.getLogger("cnpcplus")
                        .debug("[anvil] Registry删除失败(不存在) id={}", id);
                return false;
            }
            writeDisk();
            return true;
        }
    }

    /** 取配方（返回副本，防止调用方改到缓存里的对象）。客户端一律取不到，见 hasServer()。 */
    public static AnvilRecipeData get(int id) {
        if (!hasServer()) return null;
        synchronized (LOCK) {
            AnvilRecipeData d = ensure().get(id);
            return d == null ? null : d.copy();
        }
    }

    /** 全部配方（副本列表）。客户端一律返回空表，配方由服务端下发到 AnvilClientData。 */
    public static List<AnvilRecipeData> list() {
        if (!hasServer()) return new ArrayList<>();
        synchronized (LOCK) {
            List<AnvilRecipeData> out = new ArrayList<>(ensure().size());
            for (AnvilRecipeData d : ensure().values()) out.add(d.copy());
            return out;
        }
    }

    // ===== 同步（配方不进 RecipeManager，编辑列表全靠本通道，231 从 SmithingRecipeManager 摘取） =====

    /** 把配方列表同步给全部在线玩家（保存/删除后调用 = 一人创建全服可用）。 */
    public static void syncToPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            syncToPlayers(server, p);
        }
    }

    /** 单发（RequestList 打开面板首拉用）。 */
    public static void syncToPlayers(MinecraftServer server, ServerPlayer target) {
        if (target == null || server == null) return;
        List<AnvilRecipeData> list = list();
        AnvilPacketHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> target),
                new PacketAnvilSync(list));
    }
}
