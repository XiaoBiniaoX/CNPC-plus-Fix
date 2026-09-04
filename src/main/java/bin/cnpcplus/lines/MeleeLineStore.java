package bin.cnpcplus.lines;

import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「近战打击」台词的挂载点。
 *
 * 1.12.2 的 {@code DataAdvanced} 原生只有 6 组台词（交互 / 世界 / 攻击 /
 * 被击杀 / 击杀 / NPC互聊），没有「命中时」这一组。注意原生的 attackLines
 * 是在**锁定目标**时播放（{@code EntityNPCInterface.setAttackTarget}），
 * 不是命中时播放，所以这确实是一处空缺。
 *
 * 为什么用 WeakHashMap 而不是给 mixin 加 @Unique 字段：
 * 本项目已有多次教训（见 findings 阶段 23）—— 跨 mixin 访问 @Unique 成员会让
 * Mixin 转换器无法解析，必须走普通接口或普通类。这里 GUI（客户端）、实体
 * （服务端）与 NBT 读写三处都要访问同一份数据，用一个放在 mixin 包外的普通
 * 静态存储最省事，也与既有的 {@code SongListStore}、{@code DropPageStore}
 * 同一套模式。
 *
 * 弱引用键保证 NPC 卸载后条目自动回收，不会泄漏。
 */
public class MeleeLineStore {

    /** NBT 键。与其余台词键同一命名风格（Npc* + Lines）。 */
    public static final String NBT_KEY = "CNPCPlusMeleeLines";

    private static final Map<DataAdvanced, Lines> LINES = new WeakHashMap<DataAdvanced, Lines>();
    private static final Object LOCK = new Object();

    /** 取得（必要时创建）某个 NPC 的近战打击台词组。 */
    public static Lines get(DataAdvanced advanced) {
        if (advanced == null) {
            return null;
        }
        synchronized (LOCK) {
            Lines lines = LINES.get(advanced);
            if (lines == null) {
                lines = new Lines();
                LINES.put(advanced, lines);
            }
            return lines;
        }
    }

    /** 只读取，不创建。用于写 NBT 时避免给没配过台词的 NPC 凭空建条目。 */
    public static Lines peek(DataAdvanced advanced) {
        if (advanced == null) {
            return null;
        }
        synchronized (LOCK) {
            return LINES.get(advanced);
        }
    }

    public static void set(DataAdvanced advanced, Lines lines) {
        if (advanced == null) {
            return;
        }
        synchronized (LOCK) {
            if (lines == null) {
                LINES.remove(advanced);
            } else {
                LINES.put(advanced, lines);
            }
        }
    }
}
