package top.cnpcplus.ranged;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataRanged;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.DropFxCaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 弹射物粒子特效（尾迹）配置的挂载点（234 派发「尾迹类型则复用我们的粒子框架，
 * 将掉落物对象变更为投掷物」）。
 *
 * <h3>归属</h3>
 * 键 = {@code DataRanged}（NPC 远程配置实例），值 = 与掉落物配方同款的
 * {@code List<DropFxConfig>}（粒子注册 id + 形状子配置）。编辑器直接复用
 * {@code SubGuiDropFx}（234 轮改为 Consumer 回写），引擎直接复用
 * {@code DropFxEngine.spawn}（锚点只是坐标，与掉落物解耦）——数据格式一个字都不用改。
 *
 * <h3>NBT</h3>
 * 键 {@code CNPCPlusProjectileFx} 挂 DataRanged 复合标签，随 STATS 菜单包双向同步，
 * 与 {@link RangedBuffStore#NBT_KEY} 并列、与原版 {@code pTrail} 无冲突。
 * 读取后必经 {@link DropFxCaps#sanitize}（网络/磁盘来的数据一律裁剪）。
 */
public final class RangedFxStore {

    /** NBT 键（挂在 DataRanged 复合标签上，随 STATS 菜单包全量同步）。 */
    public static final String NBT_KEY = "CNPCPlusProjectileFx";
    /** 独立布尔键，原ListTag格式不变；旧数据缺省关闭。 */
    public static final String FOLLOW_KEY = "CNPCPlusProjectileFxFollow";

    /** sanitize 时给日志用的来源标识（替代掉落物侧的配方 id）。 */
    private static final String SOURCE = "NPC 弹射物特效";

    private static final Map<DataRanged, List<DropFxConfig>> FXS = new WeakHashMap<>();
    private static final Map<DataRanged, Boolean> FOLLOW = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private RangedFxStore() {
    }

    /** 取得（必要时创建）配置列表，可直接改（GUI 编辑确定时回写）。 */
    public static List<DropFxConfig> get(DataRanged ranged) {
        if (ranged == null) return DropFxCaps.newList();
        synchronized (LOCK) {
            return FXS.computeIfAbsent(ranged, k -> DropFxCaps.newList());
        }
    }

    /** 只读，不创建。写 NBT / 发射快照时用，避免给没配过的 NPC 凭空建条目。 */
    public static List<DropFxConfig> peek(DataRanged ranged) {
        if (ranged == null) return null;
        synchronized (LOCK) {
            return FXS.get(ranged);
        }
    }

    public static int size(DataRanged ranged) {
        List<DropFxConfig> list = peek(ranged);
        synchronized (LOCK) {
            return list == null ? 0 : list.size();
        }
    }

    public static boolean follows(DataRanged ranged) {
        synchronized (LOCK) { return FOLLOW.getOrDefault(ranged, false); }
    }

    public static void setFollows(DataRanged ranged, boolean follow) {
        if (ranged == null) return;
        synchronized (LOCK) {
            if (follow) FOLLOW.put(ranged, true);
            else FOLLOW.remove(ranged);
        }
    }

    /** 整表替换（编辑器确定回写）。入表即裁剪，空表清键。 */
    public static void put(DataRanged ranged, List<DropFxConfig> fxs) {
        if (ranged == null) return;
        List<DropFxConfig> copy = new ArrayList<>();
        if (fxs != null) {
            for (DropFxConfig c : fxs) {
                if (c != null) copy.add(c.copy());
            }
        }
        DropFxCaps.sanitize(copy, SOURCE);
        synchronized (LOCK) {
            if (copy.isEmpty()) {
                FXS.remove(ranged);
            } else {
                FXS.put(ranged, copy);
            }
        }
    }

    public static void clear(DataRanged ranged) {
        if (ranged == null) return;
        synchronized (LOCK) {
            FXS.remove(ranged);
            FOLLOW.remove(ranged);
        }
    }

    // ---------------------------------------------------------------- NBT

    /** 写入。空列表 remove 键（「清空特效」能正确落盘）。 */
    public static void write(DataRanged ranged, CompoundTag compound) {
        if (compound == null) return;
        List<DropFxConfig> list = peek(ranged);
        synchronized (LOCK) {
            if (follows(ranged)) compound.putBoolean(FOLLOW_KEY, true);
            else compound.remove(FOLLOW_KEY);
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
    public static void read(DataRanged ranged, CompoundTag compound) {
        if (ranged == null || compound == null) return;
        List<DropFxConfig> list = new ArrayList<>();
        if (compound.contains(NBT_KEY, Tag.TAG_LIST)) {
            ListTag tags = compound.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < tags.size(); i++) {
                list.add(DropFxConfig.fromNBT(tags.getCompound(i)));
            }
        }
        DropFxCaps.sanitize(list, SOURCE);
        synchronized (LOCK) {
            setFollows(ranged, compound.getBoolean(FOLLOW_KEY));
            if (list.isEmpty()) {
                FXS.remove(ranged);
            } else {
                FXS.put(ranged, list);
            }
        }
    }
}
