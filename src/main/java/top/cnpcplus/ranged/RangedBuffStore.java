package top.cnpcplus.ranged;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataRanged;
import top.cnpcplus.melee.BuffTable;
import top.cnpcplus.melee.MeleeBuffStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 弹射物附加 BUFF 列表的挂载点（234 派发「效果可以直接抄作业……不过需要注意对象」）。
 *
 * <h3>为什么平行一份而不是复用 MeleeBuffStore 的表</h3>
 * CNPC 的近战与远程是两个独立 data 对象（{@code DataMelee} / {@code DataRanged}），
 * 两者**无共享接口方法**（{@code setEffect(int,int,int)} 同签名但分属 INPCMelee/INPCRanged），
 * 所以 WeakHashMap 的键必须分表。GUI 侧通过 {@link BuffTable} 接口共用一份
 * {@code SubGuiMeleeBuffs}，纯函数（{@link MeleeBuffStore#resolve} / {@code clamp*} /
 * {@code nameOf}、上限常量）直接委托近战侧，不复制。
 *
 * <h3>NBT</h3>
 * 键 {@code CNPCPlusRangedBuffs} 挂在 DataRanged 自己的复合标签上
 * （{@code DataStats.readToNBT} 把 ranged 平铺进 NPC 根标签），
 * 随 {@code SPacketMenuSave/Get(STATS)} 双向同步并进存档，零新增网络包——
 * 与近战 {@code CNPCPlusMeleeBuffs} 同套路，两键不同名无冲突。
 *
 * <p>其余语义（注册名字符串键防 id 漂移、LinkedHashMap 保序、全量覆盖读、
 * 空表 remove 键、全部写操作加锁）全部与 {@link MeleeBuffStore} 一致，见其类注释。
 */
public final class RangedBuffStore {

    /** NBT 键（挂在 DataRanged 复合标签上，随 STATS 菜单包全量同步）。 */
    public static final String NBT_KEY = "CNPCPlusRangedBuffs";

    private static final Map<DataRanged, Map<String, int[]>> BUFFS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private RangedBuffStore() {
    }

    /** 取得（必要时创建）某个 NPC 远程配置的 BUFF 表。返回实表，可直接改。 */
    public static Map<String, int[]> get(DataRanged ranged) {
        if (ranged == null) return new LinkedHashMap<>();
        synchronized (LOCK) {
            return BUFFS.computeIfAbsent(ranged, k -> new LinkedHashMap<>());
        }
    }

    /** 只读，不创建。写 NBT / 发射快照时用。 */
    public static Map<String, int[]> peek(DataRanged ranged) {
        if (ranged == null) return null;
        synchronized (LOCK) {
            return BUFFS.get(ranged);
        }
    }

    public static int size(DataRanged ranged) {
        Map<String, int[]> map = peek(ranged);
        synchronized (LOCK) {
            return map == null ? 0 : map.size();
        }
    }

    public static void put(DataRanged ranged, String registryName, int amplifier, int seconds) {
        if (ranged == null || registryName == null || registryName.isEmpty()) return;
        Map<String, int[]> map = get(ranged);
        synchronized (LOCK) {
            if (!map.containsKey(registryName) && map.size() >= MeleeBuffStore.MAX_ENTRIES) return;
            map.put(registryName, new int[]{
                    MeleeBuffStore.clampAmplifier(amplifier),
                    MeleeBuffStore.clampSeconds(seconds)
            });
        }
    }

    public static void remove(DataRanged ranged, String registryName) {
        Map<String, int[]> map = peek(ranged);
        if (map == null || registryName == null) return;
        synchronized (LOCK) {
            map.remove(registryName);
        }
    }

    public static void clear(DataRanged ranged) {
        Map<String, int[]> map = peek(ranged);
        if (map == null) return;
        synchronized (LOCK) {
            map.clear();
        }
    }

    /** 给 {@code SubGuiMeleeBuffs} 用的实例级视图（与 MeleeBuffStore.tableFor 平行）。 */
    public static BuffTable tableFor(DataRanged ranged) {
        return new BuffTable() {
            @Override
            public Map<String, int[]> get() {
                return RangedBuffStore.get(ranged);
            }

            @Override
            public void put(String registryName, int amplifier, int seconds) {
                RangedBuffStore.put(ranged, registryName, amplifier, seconds);
            }

            @Override
            public void remove(String registryName) {
                RangedBuffStore.remove(ranged, registryName);
            }

            @Override
            public void clear() {
                RangedBuffStore.clear(ranged);
            }
        };
    }

    // ---------------------------------------------------------------- NBT

    /** 写入。空表明确 remove 键，让「清空了 BUFF」能正确落盘。 */
    public static void write(DataRanged ranged, CompoundTag compound) {
        if (compound == null) return;
        Map<String, int[]> map = peek(ranged);
        synchronized (LOCK) {
            if (map == null || map.isEmpty()) {
                compound.remove(NBT_KEY);
                return;
            }
            ListTag list = new ListTag();
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                CompoundTag tag = new CompoundTag();
                tag.putString("Effect", entry.getKey());
                tag.putInt("Amp", MeleeBuffStore.clampAmplifier(entry.getValue()[0]));
                tag.putInt("Seconds", MeleeBuffStore.clampSeconds(entry.getValue()[1]));
                list.add(tag);
            }
            compound.put(NBT_KEY, list);
        }
    }

    /** 读取。全量覆盖语义（与原版 DataRanged.load 无条件覆写一致）。 */
    public static void read(DataRanged ranged, CompoundTag compound) {
        if (ranged == null || compound == null) return;
        Map<String, int[]> map = get(ranged);
        synchronized (LOCK) {
            map.clear();
            if (!compound.contains(NBT_KEY, Tag.TAG_LIST)) return;
            ListTag list = compound.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && map.size() < MeleeBuffStore.MAX_ENTRIES; i++) {
                CompoundTag tag = list.getCompound(i);
                String name = tag.getString("Effect");
                if (name == null || name.isEmpty()) continue;
                map.put(name, new int[]{
                        MeleeBuffStore.clampAmplifier(tag.getInt("Amp")),
                        MeleeBuffStore.clampSeconds(tag.getInt("Seconds"))
                });
            }
        }
    }
}
