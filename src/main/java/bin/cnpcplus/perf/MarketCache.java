package bin.cnpcplus.perf;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.CustomNpcs;
import noppes.npcs.util.NBTJsonUtil;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化 #4：商店市场文件的内存缓存。
 *
 * <h2>原版做了什么</h2>
 * {@code RoleTrader.interact(Player)}（反编译源码 95-104 行）—— 玩家<b>每次</b>右键商人 NPC：
 * <pre>
 * this.npc.say(player, this.npc.advanced.getInteractLine());
 * try { RoleTrader.load(this, this.marketName); }      // ← 同步读盘 + JSON 解析
 * catch (Exception ex) { ... }
 * NoppesUtilServer.sendOpenGui(player, EnumGuiType.PlayerTrader, this.npc);
 * </pre>
 *
 * {@code RoleTrader.load}（192-206 行）：
 * <pre>
 * if (role.npc.level().isClientSide) return;
 * File file = RoleTrader.getFile(name);               // new File + dir.exists() + 可能 mkdir
 * if (!file.exists()) return;                        // 又一次文件系统调用
 * try { role.readNBT(NBTJsonUtil.LoadFile(file)); }   // 读全文 + 手写 JSON 解析器
 * catch (Exception e) { }
 * </pre>
 *
 * {@code getFile}（208-213 行）每次都 {@code new File(levelDir, "markets")}
 * + {@code dir.exists()}（syscall）+ 可能 {@code dir.mkdir()}（syscall）
 * + {@code new File(dir, name.toLowerCase() + ".json")}。
 *
 * <h2>为什么这条路径贵</h2>
 * {@code NBTJsonUtil} 不是 Gson，是 CNPC 自己手写的字符串 JSON → NBT 解析器
 * （{@code noppes/npcs/util/NBTJsonUtil.java}，逐字符切串、大量 {@code substring}）。
 * 一个满配商店有 36 格货币 + 18 格商品 = 54 个 {@code ItemStack} 的完整 NBT，
 * 解析出来是几百个 {@code CompoundTag} / {@code StringTag} 对象。
 *
 * <p>叠加起来：每次右键都是「4 次以上 syscall + 一次全文件读 + 几百个临时对象」，
 * 全在服务器主线程上同步执行。一个热闹的商业区里几个玩家反复开关商店，
 * 这就是可感知的 TPS 抖动。
 *
 * <h2>本类怎么做</h2>
 * 一层 {@code marketName（小写） -> CompoundTag} 的内存缓存。
 * <ul>
 *   <li><b>读</b>：命中缓存直接返回，零 IO 零解析。未命中才读盘并入缓存。</li>
 *   <li><b>写</b>：更新缓存 + <b>同步落盘</b>。</li>
 *   <li>还缓存 {@code File} 对象与目录检查结果，消掉重复 syscall。</li>
 * </ul>
 *
 * <h2>为什么写是同步的</h2>
 * 用户明确要求「保住兼容性」。异步写盘会引入一个真实风险：
 * 服务器崩溃或强制关闭的瞬间，还在队列里的市场编辑会丢。
 *
 * <p>而写路径的频率本来就极低 —— 只在两种情况触发：
 * <ol>
 *   <li>管理员在 GUI 里编辑商店后 {@code toSave} 被置位（{@code save} 第 64-66 行）</li>
 *   <li>{@code setMarket} 首次为一个新市场名创建文件（219-221 行）</li>
 * </ol>
 * 也就是「配置时」而不是「运行时」。同步写在这个频率上完全不构成性能问题，
 * 而缓存已经把真正的热路径（玩家交互读取）优化到零 IO。
 *
 * <h2>缓存一致性：多个 NPC 共享同一市场名</h2>
 * 这是 CNPC 市场功能的核心用法 —— 多个商人共享一份库存。
 * 原版靠「每次交互都重读文件」来实现这种共享。
 *
 * <p>缓存必须保持同样的语义，所以：
 * <ul>
 *   <li>{@link #load} 返回的是缓存条目的 {@link CompoundTag#copy() 深拷贝}。
 *       不拷贝的话，A 商人的 {@code readNBT} 之后 B 商人的物品栏对象会和缓存里的
 *       NBT 产生别名关系，一个改动会污染另一个。</li>
 *   <li>{@link #store} 一律覆盖缓存条目，所以任意一个 NPC 的保存会立刻对
 *       其他共享同名市场的 NPC 可见 —— 与原版「写文件、别人下次读到」一致，
 *       而且比原版更及时（原版要等对方下次交互重读）。</li>
 * </ul>
 *
 * <h2>与商人多页附属的兼容</h2>
 * 这是本项优化最需要小心的地方。同目录的 {@code top.cnpcplus} 附属有一个
 * {@code MixinRoleTraderPages}，它挂在 {@code RoleTrader} 的两个方法上：
 * <pre>
 * &#64;Inject(method = "writeNBT", at = &#64;At("HEAD"))    → TraderPager.flushCurrent(role)
 * &#64;Inject(method = "writeNBT", at = &#64;At("RETURN"))  → 往 nbt 里写 TraderPages / PageTitles / FullPages
 * &#64;Inject(method = "readNBT",  at = &#64;At("RETURN"))  → TraderPager.fromNBT(role, nbt)
 * </pre>
 *
 * <p>也就是说<b>多页数据是搭 {@code writeNBT}/{@code readNBT} 的车走的</b>，
 * 并不在 {@code RoleTrader} 自己的字段里。
 *
 * <p>因此本类的设计红线是：<b>绝不绕过 {@code writeNBT} 和 {@code readNBT}</b>。
 * <ul>
 *   <li>写缓存时，NBT 由 {@code role.writeNBT(new CompoundTag())} 产出 ——
 *       多页附属的 RETURN 注入会把 {@code TraderPages} 塞进来，一起进缓存、一起落盘。</li>
 *   <li>读缓存时，把 NBT 交给 {@code role.readNBT(tag)} ——
 *       多页附属的 RETURN 注入会从里面取出 {@code TraderPages} 恢复分页。</li>
 * </ul>
 * 这样多页数据与本缓存是<b>透明共存</b>的：缓存看到的就是「包含多页字段的完整 NBT」，
 * 它不知道也不需要知道哪些字段是谁写的。
 *
 * <p>同一个道理对任何其他挂在 {@code writeNBT}/{@code readNBT} 上的第三方附属也成立。
 *
 * <p><b>1.21.1 已核</b>：{@code CustomNpcs.getLevelSaveDirectory()}、
 * {@code NBTJsonUtil.LoadFile(File)}/{@code SaveFile(File, CompoundTag)} 在 1.21.1 均存在，
 * 本类无需改名。
 */
public final class MarketCache {

    /** {@code marketName（已小写）-> 完整市场 NBT}。 */
    private static final Map<String, CompoundTag> CACHE = new ConcurrentHashMap<>();

    /** {@code marketName（已小写）-> File}，省掉重复的 {@code new File} 与目录检查。 */
    private static final Map<String, File> FILES = new ConcurrentHashMap<>();

    /** markets 目录。首次使用时创建，之后不再做 exists/mkdir 的 syscall。 */
    private static volatile File marketDir;

    private MarketCache() {
    }

    /**
     * 取市场 NBT。
     *
     * @return 缓存条目的深拷贝；文件不存在或解析失败时返回 {@code null}
     */
    public static CompoundTag load(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String key = name.toLowerCase();

        CompoundTag cached = CACHE.get(key);
        if (cached != null) {
            // 必须拷贝：否则多个共享同名市场的 NPC 会与缓存条目产生别名，互相污染。
            return cached.copy();
        }

        File file = fileFor(key);
        if (!file.exists()) {
            return null;
        }
        try {
            CompoundTag parsed = NBTJsonUtil.LoadFile(file);
            if (parsed == null) {
                return null;
            }
            CACHE.put(key, parsed);
            return parsed.copy();
        } catch (Exception e) {
            // 与原版 203-205 行一致：解析失败静默返回，不改动 role 的现有库存。
            return null;
        }
    }

    /**
     * 写市场 NBT：更新缓存并同步落盘。
     *
     * <p>落盘沿用原版的「写临时文件再改名」策略（{@code RoleTrader.save} 178-189 行），
     * 这样写入过程中崩溃不会留下半个损坏的 json。
     *
     * @param tag 完整市场 NBT。<b>调用方必须传 {@code role.writeNBT(...)} 的产物</b>，
     *            否则第三方附属（如商人多页）搭车的字段会丢失。
     */
    public static void store(String name, CompoundTag tag) {
        if (name == null || name.isEmpty() || tag == null) {
            return;
        }
        String key = name.toLowerCase();
        // 存进缓存的是拷贝：调用方后续如果复用了这个 CompoundTag（比如又往里塞别的键），
        // 不应该影响缓存内容。
        CACHE.put(key, tag.copy());

        File target = fileFor(key);
        File temp = new File(target.getParentFile(), key + "_new.json");
        try {
            NBTJsonUtil.SaveFile(temp, tag);
            if (target.exists() && !target.delete()) {
                // 删不掉说明被占用。放弃改名，但缓存已经更新，
                // 内存里的数据是对的，只是这一次没落盘。
                return;
            }
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(target);
        } catch (Exception e) {
            // 与原版 187-189 行一致：吞掉异常。
            // 缓存已更新，所以运行期行为正确，只是这次没能持久化。
        }
    }

    /** 该市场是否已存在（缓存或磁盘）。用于 {@code setMarket} 的「不存在则先建」判断。 */
    public static boolean exists(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String key = name.toLowerCase();
        return CACHE.containsKey(key) || fileFor(key).exists();
    }

    /** 世界卸载时清空。防止切换存档后读到上一个世界的市场数据。 */
    public static void clear() {
        CACHE.clear();
        FILES.clear();
        marketDir = null;
    }

    private static File fileFor(String lowerKey) {
        File cached = FILES.get(lowerKey);
        if (cached != null) {
            return cached;
        }
        File file = new File(dir(), lowerKey + ".json");
        FILES.put(lowerKey, file);
        return file;
    }

    private static File dir() {
        File d = marketDir;
        if (d != null) {
            return d;
        }
        // 与原版 getFile 第 209-212 行一致的目录与创建逻辑，只是只做一次。
        d = new File(CustomNpcs.getLevelSaveDirectory(), "markets");
        if (!d.exists()) {
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        }
        marketDir = d;
        return d;
    }
}
