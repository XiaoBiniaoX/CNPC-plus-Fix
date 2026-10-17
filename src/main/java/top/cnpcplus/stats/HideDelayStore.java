package top.cnpcplus.stats;

import noppes.npcs.entity.data.DataStats;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 238-2：尸体隐藏延迟（tick）挂载点。渲染门与压缩门共用同一值，
 * NBT 键 {@code CNPCPlusHideDelay} 挂 DataStats（见 MixinDataStatsFloat）。
 * 未配置 = 默认 5 tick（旧行为硬编码 20）。
 */
public final class HideDelayStore {

    /** 未配置时的默认隐藏延迟（tick）。旧硬编码渲染门=20，用户要求的新默认=5。 */
    public static final int DEFAULT_DELAY = 5;

    /** 上限：与重生时间输入框一致的宽松上限，防极端值卡渲染。 */
    public static final int MAX_DELAY = 1200;

    private static final Map<DataStats, Integer> DELAYS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private HideDelayStore() {
    }

    /** 取延迟，未配置返回默认 5。渲染门（客户端）与压缩门（双端）都读这个。 */
    public static int get(DataStats stats) {
        if (stats == null) return DEFAULT_DELAY;
        synchronized (LOCK) {
            return DELAYS.getOrDefault(stats, DEFAULT_DELAY);
        }
    }

    /** 写入（重生界面输入框回写）。夹取 0..MAX_DELAY。 */
    public static void put(DataStats stats, int delay) {
        if (stats == null) return;
        delay = Math.max(0, Math.min(MAX_DELAY, delay));
        synchronized (LOCK) {
            DELAYS.put(stats, delay);
        }
    }

    /** 清配置（输入框置空/删除时回落默认）。 */
    public static void clear(DataStats stats) {
        if (stats == null) return;
        synchronized (LOCK) {
            DELAYS.remove(stats);
        }
    }
}
