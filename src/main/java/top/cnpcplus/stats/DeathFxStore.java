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

/** 对齐1.20.1：死亡粒子复用掉落框架，随原生STATS菜单NBT双向同步。 */
public final class DeathFxStore {
    public static final String NBT_KEY = "CNPCPlusDeathFx";
    private static final String SOURCE = "NPC 死亡粒子";
    private static final Map<DataStats, List<DropFxConfig>> FXS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private DeathFxStore() {}

    public static List<DropFxConfig> get(DataStats stats) {
        if (stats == null) return DropFxCaps.newList();
        synchronized (LOCK) { return FXS.computeIfAbsent(stats, k -> DropFxCaps.newList()); }
    }

    public static List<DropFxConfig> peek(DataStats stats) {
        if (stats == null) return null;
        synchronized (LOCK) { return FXS.get(stats); }
    }

    public static int size(DataStats stats) {
        synchronized (LOCK) {
            List<DropFxConfig> list = peek(stats);
            return list == null ? 0 : list.size();
        }
    }

    public static void put(DataStats stats, List<DropFxConfig> fxs) {
        if (stats == null) return;
        List<DropFxConfig> copy = new ArrayList<>();
        if (fxs != null) for (DropFxConfig c : fxs) if (c != null) copy.add(c.copy());
        DropFxCaps.sanitize(copy, SOURCE);
        synchronized (LOCK) {
            if (copy.isEmpty()) FXS.remove(stats);
            else FXS.put(stats, copy);
        }
    }

    public static void clear(DataStats stats) {
        if (stats == null) return;
        synchronized (LOCK) { FXS.remove(stats); }
    }

    public static void write(DataStats stats, CompoundTag compound) {
        if (compound == null) return;
        synchronized (LOCK) {
            List<DropFxConfig> list = peek(stats);
            if (list == null || list.isEmpty()) { compound.remove(NBT_KEY); return; }
            ListTag tags = new ListTag();
            for (DropFxConfig c : list) tags.add(c.toNBT());
            compound.put(NBT_KEY, tags);
        }
    }

    public static void read(DataStats stats, CompoundTag compound) {
        if (stats == null || compound == null) return;
        List<DropFxConfig> list = new ArrayList<>();
        if (compound.contains(NBT_KEY, Tag.TAG_LIST)) {
            ListTag tags = compound.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < tags.size(); i++) list.add(DropFxConfig.fromNBT(tags.getCompound(i)));
        }
        DropFxCaps.sanitize(list, SOURCE);
        synchronized (LOCK) {
            if (list.isEmpty()) FXS.remove(stats);
            else FXS.put(stats, list);
        }
    }
}
