package top.cnpcplus.stats;

import noppes.npcs.entity.data.DataStats;
import java.util.Map;
import java.util.WeakHashMap;

/** 终版尸体隐藏延迟，挂DataStats并随原生STATS链同步。 */
public final class HideDelayStore {
    public static final int DEFAULT_DELAY = 5;
    public static final int MAX_DELAY = 1200;
    private static final Map<DataStats, Integer> DELAYS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private HideDelayStore() {}

    public static int get(DataStats stats) {
        if (stats == null) return DEFAULT_DELAY;
        synchronized (LOCK) { return DELAYS.getOrDefault(stats, DEFAULT_DELAY); }
    }

    public static void put(DataStats stats, int delay) {
        if (stats == null) return;
        synchronized (LOCK) { DELAYS.put(stats, Math.max(0, Math.min(MAX_DELAY, delay))); }
    }

    public static void clear(DataStats stats) {
        if (stats == null) return;
        synchronized (LOCK) { DELAYS.remove(stats); }
    }
}
