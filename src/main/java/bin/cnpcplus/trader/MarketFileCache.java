package bin.cnpcplus.trader;

import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商人市场文件的解析结果缓存（性能问题 6）。
 *
 * <h3>为什么需要</h3>
 * {@code RoleTrader.interact} 的 offset 23 无条件 {@code invokestatic load}，
 * 没有任何缓存判断。每次玩家右键商人都要走一遍：
 * <pre>
 * getFile()                 → 3 次 File.exists()（含 getWorldSaveDirectory 内部的 mkdirs 检查）
 * NBTJsonUtil.LoadFile()    → Guava Files.toString 读全文 + 自研逐字符 JSON 解析器
 *                             （ReadValue 单方法 442 行字节码，递归下降）
 * readNBT()                 → 54 个槽位反序列化
 * </pre>
 * 全程在**服务端主线程同步执行**，市场文件越大越卡 —— 这是玩家肉眼可感的卡顿。
 *
 * <h3>关键设计：只缓存解析结果，不跳过 readNBT（保住我们的翻页功能）</h3>
 * 哈基彬明确担心「我们的翻页会因为这个死掉」，核实后确认风险成立：
 * 我们自己的 {@code MixinRoleTraderPages} 在 **{@code readNBT} 的 RETURN**
 * 注入 {@code TraderPager.fromNBT(...)} 来重建分页数据。
 * 若缓存命中就整体跳过 {@code load}，{@code fromNBT} 永不被调用 → 翻页数据丢失。
 *
 * 所以本缓存只替换 {@code LoadFile}（磁盘 IO + JSON 解析）这一步的结果，
 * {@code readNBT} 照常执行 → 翻页重建链完整保留。
 *
 * <h3>失效策略</h3>
 * 用 {@code lastModified()} + {@code length()} 双字段校验。任一变化即视为文件被
 * 外部改动（手工编辑、其他工具写入），重新读盘。
 * 只比时间戳不够：某些文件系统的时间戳精度是秒级，同秒内的两次写入会漏判。
 *
 * <h3>返回值的防御性拷贝</h3>
 * {@code readNBT} 会不会改动传入的 NBT？不确定，且 CNPC 未来版本可能会。
 * 所以命中缓存时返回 {@code copy()}，避免调用方污染缓存里的那份。
 * 一次 NBT 拷贝远比「读全文 + 递归解析」便宜。
 *
 * <h3>为什么放在 bin.cnpcplus.trader 而不是 mixin 包内</h3>
 * 阶段 22 的 {@code IllegalClassLoadError} 教训：mixin 包内的非 mixin 类被目标类
 * 引用会导致类加载失败。这里与既有的 {@code TraderPager} 同包。
 */
public final class MarketFileCache {

    /** 缓存模式：0 = 关闭（原版行为）/ 1 = 时间戳校验 / 2 = 纯内存不校验。 */
    public static final int MODE_OFF = 0;
    public static final int MODE_VALIDATE = 1;
    public static final int MODE_MEMORY = 2;

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<String, Entry>();

    private MarketFileCache() {
    }

    private static final class Entry {
        final NBTTagCompound data;
        final long lastModified;
        final long length;

        Entry(NBTTagCompound data, long lastModified, long length) {
            this.data = data;
            this.lastModified = lastModified;
            this.length = length;
        }
    }

    /**
     * 取缓存。未命中或已失效返回 null，调用方应自行读盘后调 {@link #put}。
     *
     * @param mode 见 MODE_* 常量，由 cfg 提供，便于随时退回原版行为
     */
    public static NBTTagCompound get(File file, int mode) {
        if (mode == MODE_OFF || file == null) return null;
        Entry entry = CACHE.get(file.getAbsolutePath());
        if (entry == null) return null;
        if (mode == MODE_VALIDATE) {
            // 时间戳或长度任一变化 → 文件被外部改过，作废。
            if (entry.lastModified != file.lastModified() || entry.length != file.length()) {
                CACHE.remove(file.getAbsolutePath());
                return null;
            }
        }
        // 防御性拷贝，避免 readNBT 或其他消费者污染缓存内容。
        return entry.data.copy();
    }

    public static void put(File file, NBTTagCompound data, int mode) {
        if (mode == MODE_OFF || file == null || data == null) return;
        CACHE.put(file.getAbsolutePath(),
                new Entry(data.copy(), file.lastModified(), file.length()));
    }

    /** 市场被保存时调用，保证下次读取拿到新数据。 */
    public static void invalidate(File file) {
        if (file != null) CACHE.remove(file.getAbsolutePath());
    }

    /** 世界卸载时清空，避免跨存档串数据。 */
    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }
}
