package bin.cnpcplus.spawner;

import noppes.npcs.roles.JobSpawner;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 召唤师职业的召唤冷却（CD）。
 *
 * <p>1.20.1 的 CNPC 原版本来就有这个功能（`JobSpawner.spawnDelay` 字段 + `nextSpawnTime`
 * 计时 + `SpawnDelay` NBT 键 + GUI 的 Delay 输入框），1.21.1 版本把整条链删掉了，
 * 所以界面上没有 CD 框、召唤也没有间隔。这里按 1.20.1 原版语义补回来。
 *
 * <p>为什么用外部表而不是 mixin 的 @Unique 字段：CD 值要在「数据层（保存/加载/计时）」
 * 和「客户端 GUI（读写输入框）」两侧访问，同一个 JobSpawner 实例在客户端 GUI 里也持有，
 * 用一张以实例为键的表比在两个 mixin 之间传字段更省事，也与本项目 SongListStore 的既有写法一致。
 *
 * <p>刻意放在 mixin 包之外：mixin 包内的类不能被外部直接引用，否则触发 IllegalClassLoadError。
 */
public final class SpawnDelayStore {

    private SpawnDelayStore() {
    }

    /** 配置的召唤间隔，单位 tick，沿用 1.20.1 原版语义。 */
    private static final Map<JobSpawner, Integer> DELAY = new WeakHashMap<>();

    /** 下一次允许召唤的游戏时间（gameTime），仅服务端计时用。 */
    private static final Map<JobSpawner, Long> NEXT = new WeakHashMap<>();

    public static synchronized int getDelay(JobSpawner job) {
        Integer v = DELAY.get(job);
        return v == null ? 0 : v;
    }

    public static synchronized void setDelay(JobSpawner job, int delay) {
        if (job == null) return;
        DELAY.put(job, Math.max(0, delay));
    }

    public static synchronized long getNextTime(JobSpawner job) {
        Long v = NEXT.get(job);
        return v == null ? 0L : v;
    }

    public static synchronized void setNextTime(JobSpawner job, long time) {
        if (job == null) return;
        NEXT.put(job, time);
    }
}
