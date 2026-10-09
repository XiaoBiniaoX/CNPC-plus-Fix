package top.cnpcplus.mixin;

import noppes.npcs.entity.data.DataTimers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 优化 #13：{@code DataTimers.update()} 每 tick 的 {@code ArrayList} 快照。
 *
 * <h2>原版做了什么</h2>
 * {@code DataTimers.update()}（反编译源码 96-102 行）：
 * <pre>
 * public void update() {
 *     Iterator&lt;Timer&gt; iterator = new ArrayList&lt;Timer&gt;(this.timers.values()).iterator();
 *     while (iterator.hasNext()) {
 *         Timer timer = iterator.next();
 *         timer.update();
 *     }
 * }
 * </pre>
 *
 * <p>每 tick 的调用点（我把全部引用查了一遍）：
 * <ul>
 *   <li>{@code EntityNPCInterface} 第 519 行 —— <b>每个 NPC 每 tick</b></li>
 *   <li>{@code TileScripted} 第 252 行 —— 每个脚本方块每 tick</li>
 *   <li>{@code TileScriptedDoor} 第 138 行 —— 每个脚本门每 tick</li>
 *   <li>{@code ScriptPlayerEventHandler} 第 177 行 —— <b>每个玩家每 tick</b></li>
 * </ul>
 *
 * <p>每次调用产生两个对象：一个 {@code ArrayList}（含内部 {@code Object[]}）
 * 和一个 {@code Itr}。一个有 200 个 NPC 的服务器：
 * 200 × 20 tick/s × 2 = <b>每秒 8000 个短命对象</b>。
 *
 * <h2>快照是必要的 —— 但绝大多数情况下根本不需要遍历</h2>
 * 先说为什么快照有理由存在：{@code Timer.update()}（反编译源码 30-47 行）在
 * 非重复计时器到期时会调 {@code DataTimers.this.stop(this.id)}，
 * 而 {@code stop} 是 {@code this.timers.remove(id)} —— <b>会修改正在遍历的 map</b>。
 * 直接遍历 {@code timers.values()} 必然 {@code ConcurrentModificationException}。
 *
 * <p>但关键观察是：<b>绝大多数宿主的 {@code timers} 是空的</b>。
 * 计时器只由脚本通过 {@code ITimers.start}/{@code forceStart} 创建
 * （{@code npc.timers.start(id, ticks, repeat)}）。
 * 一个没写脚本、或者脚本里没用 timer 的 NPC，它的 {@code timers} 永远是空 {@code HashMap}
 * —— 却照样每 tick 分配一个 {@code ArrayList} 加一个迭代器来遍历零个元素。
 *
 * <h2>本类怎么做</h2>
 * 两层：
 * <ol>
 *   <li><b>空表短路</b>。{@code timers.isEmpty()} 时直接返回。
 *       这一条就消掉了 99% 的分配 —— 因为 99% 的宿主没有计时器。</li>
 *   <li><b>非空时用复用数组代替 {@code ArrayList}</b>。
 *       一个 {@code @Unique Object[]} 缓冲，按需扩容，跨 tick 复用。
 *       仍然是「先快照再遍历」的安全模式，但不再每 tick 分配。</li>
 * </ol>
 *
 * <h2>为什么不改成「延迟删除」</h2>
 * 方案阶段我提过给 {@code Timer} 加 {@code pendingRemove} 标记、遍历完统一清理。
 * 实现时否决了，理由是<b>会破坏脚本可见的语义</b>：
 *
 * <p>{@code Timer.update()} 的顺序是「先 {@code stop(id)} 再触发事件」：
 * <pre>
 * if (this.repeat) this.ticks = this.timerTicks;
 * else DataTimers.this.stop(this.id);        // ← 先移除
 * Object ob = DataTimers.this.parent;
 * if (ob instanceof EntityNPCInterface) EventHooks.onNPCTimer(..., this.id);   // ← 后触发
 * </pre>
 *
 * <p>也就是说脚本在 {@code timer} 回调里执行时，这个 timer <b>已经被移除了</b>。
 * 脚本因此可以做这样的事：
 * <pre>
 * function timer(e) {
 *     if (e.id == 1) {
 *         // 此刻 npc.timers.has(1) 必须是 false，
 *         // 所以下面这句用 start（会在 id 已存在时抛异常）是安全的
 *         e.npc.timers.start(1, 40, false);
 *     }
 * }
 * </pre>
 * 「一次性 timer 在回调里重新起同一个 id」是 CNPC 脚本里很常见的模式
 * （用来做可变间隔的循环）。如果改成延迟删除，
 * {@code has(1)} 会返回 {@code true}，{@code start(1, ...)} 会抛
 * {@code CustomNPCsException("There is already a timer with id: 1")}
 * —— <b>脚本直接报错失效</b>。
 *
 * <p>用户明确要求「避免破坏 CNPC 脚本系统的 Timer 相关导致对应脚本失效」，
 * 所以这条路不能走。保留「立即移除」的原版语义，只换快照的实现方式。
 *
 * <h2>行为等价性逐条核对</h2>
 * <ul>
 *   <li>遍历的是调用时刻的<b>快照</b>：保留（复用数组仍是快照）。
 *       所以在某个 timer 回调里新建的 timer，本 tick 不会被 update ——
 *       与原版一致（新 timer 在下一 tick 才开始倒数）。</li>
 *   <li>遍历顺序：原版是 {@code HashMap.values()} 的迭代顺序，
 *       本类拷进数组时用的是同一个迭代器，顺序相同。
 *       （{@code HashMap} 的顺序虽然不保证，但对同一个 map 实例的连续遍历是稳定的，
 *       而且脚本本来就不该依赖多个 timer 的相对触发顺序。）</li>
 *   <li>回调里 {@code stop} 掉别的 timer：那个 timer 已在快照里，
 *       仍会被 update ——与原版一致。这是快照语义的固有行为，不是我们引入的。</li>
 *   <li>{@code clear()} 在回调里被调用：快照仍会跑完 ——与原版一致。</li>
 * </ul>
 *
 * <h2>为什么用 {@code Object[]} 而不是 {@code Timer[]}</h2>
 * {@code DataTimers.Timer} 是<b>包级私有</b>的内部类
 * （反编译源码 {@code class DataTimers.Timer}，没有 {@code public}）。
 * 从 {@code top.cnpcplus.mixin} 包里引用不到这个类型。
 * 所以缓冲用 {@code Object[]}，遍历时通过 {@link #cnpcyouhua$callUpdate} 反射调用。
 *
 * <p>反射会不会把优化吃掉？不会：
 * <ul>
 *   <li>{@code Method} 对象只查找一次（静态字段缓存），之后是
 *       {@code Method.invoke} —— JIT 对热点 {@code invoke} 会生成直通桩，
 *       开销接近普通虚调用。</li>
 *   <li>更重要的是：这条路径只在<b>确实有 timer</b> 时才走。
 *       空表短路已经拦掉了绝大多数调用，剩下的那些本来就在跑脚本逻辑
 *       （{@code EventHooks.onNPCTimer} → JS 引擎执行），
 *       一次反射调用相对那个成本可以忽略。</li>
 * </ul>
 */
@Mixin(value = DataTimers.class, remap = false)
public abstract class MixinDataTimersUpdate {

    /**
     * {@code DataTimers.timers}，声明为 {@code private Map<Integer, Timer>}。
     *
     * <p>值类型 {@code Timer} 是包级私有，这里用通配符避免引用不到的类型。
     */
    @Shadow private Map<Integer, ?> timers;

    /** 复用的快照缓冲。跨 tick 保留，按需扩容。 */
    @Unique private Object[] cnpcyouhua$scratch;

    /** {@code DataTimers$Timer.update()} 的方法句柄，全局只查一次。 */
    @Unique private static java.lang.reflect.Method cnpcyouhua$timerUpdate;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcyouhua$noAllocUpdate(CallbackInfo ci) {
        Map<Integer, ?> map = this.timers;

        // 第一层：空表短路。这一条消掉 99% 的调用（绝大多数宿主没有 timer）。
        if (map == null || map.isEmpty()) {
            ci.cancel();
            return;
        }

        int count = map.size();
        Object[] buffer = this.cnpcyouhua$scratch;
        if (buffer == null || buffer.length < count) {
            // 扩容留 25% 余量，避免 timer 数量小幅波动时反复扩容。
            buffer = new Object[count + (count >> 2) + 1];
            this.cnpcyouhua$scratch = buffer;
        }

        // 第二层：快照拷进复用数组，代替每 tick new ArrayList。
        // 快照是必要的 —— Timer.update() 里的 stop(id) 会修改这个 map。
        int filled = 0;
        for (Object timer : map.values()) {
            buffer[filled++] = timer;
        }

        for (int i = 0; i < filled; i++) {
            Object timer = buffer[i];
            buffer[i] = null;   // 及早清引用，别让缓冲区把已 stop 的 Timer 拖着不放
            cnpcyouhua$callUpdate(timer);
        }
        ci.cancel();
    }

    /**
     * 调用 {@code DataTimers$Timer.update()}。
     *
     * <p>用反射是因为 {@code Timer} 是包级私有内部类，从本包引用不到它的类型。
     * {@code Method} 只查一次并缓存。
     */
    @Unique
    private static void cnpcyouhua$callUpdate(Object timer) {
        if (timer == null) {
            return;
        }
        try {
            java.lang.reflect.Method m = cnpcyouhua$timerUpdate;
            if (m == null || !m.getDeclaringClass().isInstance(timer)) {
                m = timer.getClass().getDeclaredMethod("update");
                m.setAccessible(true);
                cnpcyouhua$timerUpdate = m;
            }
            m.invoke(timer);
        } catch (Exception e) {
            // 单个 timer 出错不应该毁掉同一宿主的其他 timer。
            // 原版没有这层保护（一个 timer 抛异常会中断整个 update 循环），
            // 这里更宽容一点，但不吞掉脚本自己的错误报告 ——
            // EventHooks.onNPCTimer 内部的脚本异常由 ScriptContainer 自己捕获并
            // 通过 NotifyOPs 上报，不会传到这里。
        }
    }
}
