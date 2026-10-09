package bin.cnpcplus.perf;

import net.neoforged.bus.api.IEventBus;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.wrapper.WrapperNpcAPI;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化 #3 的判据来源：判断 CNPC 的脚本 API 事件总线上是否真的挂着监听器。
 *
 * <h2>为什么需要这个判断</h2>
 * {@code EntityNPCInterface.onCollide()} 每 4 tick 为每个 NPC 做一次
 * {@code getEntitiesOfClass(LivingEntity.class, aabb)}，然后给每个候选调
 * {@code EventHooks.onNPCCollide}。后者做的是：
 * <pre>
 * if (npc.script.isClient()) return;
 * NpcEvent.CollideEvent event = new NpcEvent.CollideEvent(npc.wrappedNPC, entity);   // 每个候选一个对象
 * npc.script.runScript(EnumScriptType.COLLIDE, event);
 * WrapperNpcAPI.EVENT_BUS.post(event);
 * </pre>
 *
 * <p>如果这个 NPC 没有启用脚本（{@code DataScript.isEnabled()} 返回 false），
 * 且 API 总线上也没有人监听 {@code CollideEvent}，
 * 那么整个「查询 + 建事件对象 + post」全是无效开销 —— 但原版照样每 4 tick 跑一遍。
 *
 * <p>脚本开关很容易判断（{@code npc.script.getEnabled()} 是 public）。
 * 麻烦的是 API 总线：{@code WrapperNpcAPI.EVENT_BUS} 是
 * {@code BusBuilder.builder().build()} 造出来的普通 {@code IEventBus}
 * （1.21.1 为 {@code net.neoforged.bus.api.IEventBus}，实现类是
 * {@code net.neoforged.bus.EventBus}），
 * 而 {@code IEventBus} 接口<b>没有提供「某事件类型有没有监听器」的公开查询</b>。
 *
 * <h2>怎么查</h2>
 * NeoForge 的 {@code EventBus} 实现内部有一个
 * {@code private final ConcurrentHashMap<Object, List<EventListener>> listeners}
 * （key 是注册时传入的对象或 Class；javap 核实字段名在 1.21.1 仍是 {@code listeners}）。
 * 只要这个 map 非空，就说明有人注册过监听器。
 *
 * <p>这里刻意<b>不去区分具体是哪个事件类型</b>，只判断「总线上有没有任何监听器」。
 * 理由：
 * <ul>
 *   <li>精确判断需要遍历 {@code ListenerList} 的内部结构，
 *       那是 bus 库的实现细节，跨版本极易失效。</li>
 *   <li>「总线完全空」是绝大多数场景（没装任何用 CNPC API 的 Java mod）。
 *       只要它成立，收益就已经拿到了。</li>
 *   <li>粗判只会让我们<b>少优化</b>，绝不会<b>错优化</b> ——
 *       有监听器时我们退回原版行为，语义 100% 安全。</li>
 * </ul>
 *
 * <h2>反射失败怎么办</h2>
 * 一旦反射拿不到字段（bus 库换了实现、模块系统拦截、被混淆），
 * 就把 {@link #busUnknown} 置 true，从此<b>永久假定总线上有监听器</b>，
 * 也就是完全退回原版行为。宁可不优化，不可改行为。
 *
 * <h2>为什么要缓存</h2>
 * 反射读字段 + {@code isEmpty()} 虽然不贵，但这是每 4 tick × 每个 NPC 的路径，
 * 一个有几百 NPC 的服务器上仍然是可观的调用量。
 * 所以按 {@link #RECHECK_INTERVAL_NANOS} 缓存结果 ——
 * mod 在运行中注册/注销 API 监听器是极罕见的事件，2 秒的滞后完全无害。
 *
 * <p><b>1.21.1 适配说明</b>：Forge → NeoForge 只改 import 与措辞
 * （{@code net.minecraftforge.eventbus.api.IEventBus} →
 * {@code net.neoforged.bus.api.IEventBus}），
 * 反射目标 {@code listeners} 字段在 1.21.1 bus-8.0.5 实现中已 javap 核实原样存在。
 */
public final class CollideEventGate {

    /** 结果缓存有效期。2 秒足够短（不会长期误判），也足够长（几乎消除反射开销）。 */
    private static final long RECHECK_INTERVAL_NANOS = 2_000_000_000L;

    private static Field listenersField;
    private static boolean busUnknown;

    private static long lastCheckNanos;
    private static boolean cachedHasListeners = true;   // 保守初值：先假定有

    /**
     * 记录每个脚本处理器「上次检查时是否启用」的结果。
     * 目前未使用，保留位以便将来做更细的按 NPC 缓存。
     */
    private static final Map<Object, Boolean> UNUSED = new ConcurrentHashMap<>(0);

    static {
        try {
            IEventBus bus = WrapperNpcAPI.EVENT_BUS;
            Field f = bus.getClass().getDeclaredField("listeners");
            f.setAccessible(true);
            // 立刻试读一次。能读通才认为可用。
            Object value = f.get(bus);
            if (value instanceof Map) {
                listenersField = f;
            } else {
                busUnknown = true;
            }
        } catch (Throwable t) {
            // 任何异常（NoSuchField / SecurityException / InaccessibleObject / 类加载失败）
            // 都退化为「假定有监听器」。
            busUnknown = true;
        }
    }

    private CollideEventGate() {
    }

    /**
     * CNPC 的脚本 API 事件总线上是否可能有 {@link NpcEvent.CollideEvent} 的监听器。
     *
     * @return {@code true} 表示「可能有」（含无法判断的情况），此时必须走原版路径
     */
    public static boolean apiMayListen() {
        if (busUnknown || listenersField == null) {
            return true;
        }
        long now = System.nanoTime();
        if (now - lastCheckNanos < RECHECK_INTERVAL_NANOS) {
            return cachedHasListeners;
        }
        lastCheckNanos = now;
        try {
            Object value = listenersField.get(WrapperNpcAPI.EVENT_BUS);
            cachedHasListeners = !(value instanceof Map<?, ?> map) || !map.isEmpty();
        } catch (Throwable t) {
            busUnknown = true;
            cachedHasListeners = true;
        }
        return cachedHasListeners;
    }
}
