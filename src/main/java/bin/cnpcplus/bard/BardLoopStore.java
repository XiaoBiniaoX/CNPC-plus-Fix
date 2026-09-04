package bin.cnpcplus.bard;

import noppes.npcs.roles.JobBard;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 吟游诗人「循环播放」开关的挂载点。
 *
 * 1.12.2 原版 {@code JobBard} 只有 minRange / maxRange / isStreamer /
 * hasOffRange / song 五个字段，**没有循环播放**（1.20.1 才有 isLooping，
 * NBT 键 BardLoops）。界面上唯一容易被误当成「循环」的是按钮 3
 * 「像唱片机一样播放 / 像背景音乐一样播放」，那其实只切 isStreamer
 * （RECORDS 定位声 vs MUSIC 全局声）。所以这里需要新增一个真正的开关。
 *
 * NBT 键沿用高版本的 {@code BardLoops}，让存档在版本间语义一致。
 *
 * 用 WeakHashMap 而不是 mixin 的 @Unique 字段：GUI（客户端）、播放决策
 * （客户端）与 NBT 读写三处要访问同一份数据，跨 mixin 访问 @Unique 成员
 * 会让 Mixin 转换器无法解析（本项目阶段 23 的崩溃教训）。这与既有的
 * {@link SongListStore} 同一套模式，且必须放在 mixin 包外
 * （阶段 15 的 NoClassDefFoundError 教训）。
 */
public class BardLoopStore {

    /** NBT 键，与 1.20.1 / 1.21.1 原版一致。 */
    public static final String NBT_KEY = "BardLoops";

    private static final Map<JobBard, Boolean> LOOP = new WeakHashMap<JobBard, Boolean>();
    private static final Object LOCK = new Object();

    public static boolean isLooping(JobBard job) {
        if (job == null) {
            return false;
        }
        synchronized (LOCK) {
            Boolean value = LOOP.get(job);
            return value != null && value.booleanValue();
        }
    }

    public static void set(JobBard job, boolean looping) {
        if (job == null) {
            return;
        }
        synchronized (LOCK) {
            if (looping) {
                LOOP.put(job, Boolean.TRUE);
            } else {
                LOOP.remove(job);
            }
        }
    }
}
