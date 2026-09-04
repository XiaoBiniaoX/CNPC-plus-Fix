package top.cnpcplus.linked;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 同步标签的「脚本同步」开关状态，以标签名作 key。
 *
 * <p>为什么不用 {@code ExtraDataStorage}：那里是 {@code WeakHashMap} + 对象 identity hash。
 * {@code LinkedNpcController.loadNpcs()} 每次重载都会 {@code new LinkedData()}，
 * 旧实例被回收后状态就丢了；而且 {@code setNBT} 时的实例与后续 {@code npc.linkedData}
 * 引用的不一定是同一个对象，读出来会是默认 false —— 这正是「开关显示开着但脚本不同步」
 * 的一个成因。标签名是稳定 key，不受实例重建影响。
 *
 * <p>大小写：{@code LinkedNpcController.getData} 用 {@code equalsIgnoreCase} 查标签，
 * 所以这里也统一小写化 key，避免「New」与「new」被当成两个标签。
 *
 * <p>线程安全：全部方法 synchronized。单人模式下客户端线程与内置服务端线程共享静态表，
 * 这条教训已写进 task_plan.md 的双端章节。
 *
 * <p>放在 mixin 包外：普通 {@code @Mixin} 类不能被外部引用（ModLauncher 会抛
 * {@code NoClassDefFoundError: ... is invalid}），而这份状态要被多个 mixin 读写。
 */
public final class LinkedSyncFlags {

    private static final Map<String, Boolean> syncScripts = new HashMap<>();

    private LinkedSyncFlags() {
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase();
    }

    public static synchronized boolean isSyncScripts(String name) {
        return syncScripts.getOrDefault(key(name), false);
    }

    public static synchronized void setSyncScripts(String name, boolean value) {
        syncScripts.put(key(name), value);
    }

    /** 给状态同步包用：拿到当前全部标签的开关快照。 */
    public static synchronized Map<String, Boolean> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(syncScripts));
    }
}
