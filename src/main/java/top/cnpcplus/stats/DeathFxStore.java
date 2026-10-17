package top.cnpcplus.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataStats;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.DropFxCaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 238-3：NPC 死亡粒子配置挂载点（{@code DataStats} 实例）。
 * 复用掉落物粒子框架（{@code DropFxEngine.spawn} 已解耦，234 尾迹先例），
 * 编辑器复用 {@code SubGuiDropFx}（Consumer 回写），触发=die RETURN 单次 burst。
 *
 * <h3>NBT</h3>
 * 键 {@code CNPCPlusDeathFx} 挂 DataStats 复合标签，随 STATS 菜单包双向同步。
 * 读取后必经 {@link DropFxCaps#sanitize}。
 */
public final class DeathFxStore {

    /** NBT 键（挂在 DataStats 复合标签上，随 STATS 菜单包全量同步）。 */
    public static final String NBT_KEY = "CNPCPlusDeathFx";

    private static final String SOURCE = "NPC 死亡粒子";

    private static final Map<DataStats, List<DropFxConfig>> FXS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private DeathFxStore() {
    }

    /** 取得（必要时创建）配置列表，可直接改（GUI 编辑确定时回写）。 */
    public static List<DropFxConfig> get(DataStats stats) {
        if (stats == null) return DropFxCaps.newList();
        synchronized (LOCK) {
            return FXS.computeIfAbsent(stats, k -> DropFxCaps.newList());
        }
    }

    /** 只读，不创建。die 触发时用，避免给没配过的 NPC 凭空建条目。 */
    public static List<DropFxConfig> peek(DataStats stats) {
        if (stats == null) return null;
        synchronized (LOCK) {
            return FXS.get(stats);
        }
    }

    public static int size(DataStats stats) {
        List<DropFxConfig> list = peek(stats);
        synchronized (LOCK) {
            return list == null ? 0 : list.size();
        }
    }

    /** 整表替换（编辑器确定回写）。入表即裁剪，空表清键。 */
    public static void put(DataStats stats, List<DropFxConfig> fxs) {
        if (stats == null) return;
        List<DropFxConfig> copy = new ArrayList<>();
        if (fxs != null) {
            for (DropFxConfig c : fxs) {
                if (c != null) copy.add(c.copy());
            }
        }
        DropFxCaps.sanitize(copy, SOURCE);
        synchronized (LOCK) {
            if (copy.isEmpty()) {
                FXS.remove(stats);
            } else {
                FXS.put(stats, copy);
            }
        }
    }

    public static void clear(DataStats stats) {
        if (stats == null) return;
        synchronized (LOCK) {
            FXS.remove(stats);
        }
    }

    // ---------------------------------------------------------------- NBT

    /** 写入。空列表 remove 键（「清空特效」能正确落盘）。 */
    public static void write(DataStats stats, CompoundTag compound) {
        if (compound == null) return;
        List<DropFxConfig> list = peek(stats);
        synchronized (LOCK) {
            if (list == null || list.isEmpty()) {
                compound.remove(NBT_KEY);
                return;
            }
            ListTag tags = new ListTag();
            for (DropFxConfig c : list) tags.add(c.toNBT());
            compound.put(NBT_KEY, tags);
        }
    }

    /** 读取。全量覆盖语义 + 入表必 sanitize。 */
    public static void read(DataStats stats, CompoundTag compound) {
        if (stats == null || compound == null) return;
        List<DropFxConfig> list = new ArrayList<>();
        if (compound.contains(NBT_KEY, Tag.TAG_LIST)) {
            ListTag tags = compound.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < tags.size(); i++) {
                list.add(DropFxConfig.fromNBT(tags.getCompound(i)));
            }
        }
        DropFxCaps.sanitize(list, SOURCE);
        synchronized (LOCK) {
            if (list.isEmpty()) {
                FXS.remove(stats);
            } else {
                FXS.put(stats, list);
            }
        }
    }
}
