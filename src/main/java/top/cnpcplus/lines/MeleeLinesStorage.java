package top.cnpcplus.lines;

import noppes.npcs.controllers.data.Lines;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「近战打击」台词的存储。
 *
 * <p>为什么不放在 {@code ExtraDataStorage}：那个类只存 float/boolean 标量，
 * 这里需要挂一个完整的 {@link Lines} 对象（8 槽 + 顺序/随机游标）。
 *
 * <p>为什么用旁挂 Map 而不是 {@code @Unique} 字段：读写方需要跨三处
 * （DataAdvanced 的 NBT 混入、m_7327_ 的触发混入、GUI 菜单混入），
 * 而按约法与既有教训，普通 mixin 类之间不能互相强转引用
 * （{@code (MixinX)(Object)y} 会让 ModLauncher 抛
 * {@code NoClassDefFoundError: ... is invalid}）。旁挂一个普通类是既定做法。
 *
 * <p>用 {@link WeakHashMap} 挂在 DataAdvanced 实例上：DataAdvanced 与 NPC 同生命周期，
 * NPC 卸载后条目自动回收，不泄漏。全部方法 synchronized —— 单人模式下客户端线程与
 * 内置服务端线程共享同一份静态表，必须同步（这条教训见 task_plan.md 双端章节）。
 *
 * <p>注意与 syncScripts 的区别：那里用 identity key 曾经踩过「实例重建导致状态丢失」，
 * 但这里的 key 是 DataAdvanced，它随 NPC 存活、不会像 LinkedData 那样被
 * loadNpcs() 周期性重建，所以 identity key 是安全的。
 */
public final class MeleeLinesStorage {

    private static final Map<Object, Lines> data = new WeakHashMap<>();

    private MeleeLinesStorage() {
    }

    /** 取该 DataAdvanced 的近战打击台词，不存在则创建空的（永不返回 null）。 */
    public static synchronized Lines get(Object advanced) {
        return data.computeIfAbsent(advanced, k -> new Lines());
    }

    /** 只读探测，不创建条目。给「有没有配过台词」这类判断用，避免给每个 NPC 都建对象。 */
    public static synchronized Lines peek(Object advanced) {
        return data.get(advanced);
    }
}
