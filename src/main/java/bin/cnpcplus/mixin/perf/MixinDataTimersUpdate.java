package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import noppes.npcs.entity.data.DataTimers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 优化 #13（1.20.1 主内容移植）：消除 {@code DataTimers.update()} 每 tick 的无条件列表拷贝。
 *
 * <h2>原版做了什么（javap 实证，1.12.2 05Jul20）</h2>
 * <pre>
 * public void update();
 *   0: new ArrayList
 *   4: getfield timers:Ljava/util/Map;
 *   8: invokeinterface Map.values
 *  13: invokespecial ArrayList.&lt;init&gt;(Collection)   ← 每 tick 一次全量拷贝
 *  16: invokevirtual ArrayList.iterator
 *  20: 循环: Timer.update()
 *  46: return
 * </pre>
 * 跑得动的 NPC 通常一个计时器都没有，却每 tick 为它拷贝一个空集合。
 * 计时器是「事件驱动」的：只有配了计时器事件的 NPC 才有内容。
 *
 * <h2>为什么非空时也要保留快照</h2>
 * 原版先拷贝再迭代不是多余的：{@code Timer.update()} 在计时结束且非循环时
 * 会从 {@code timers} 里移除自己。直接迭代 {@code timers.values()} 会
 * ConcurrentModificationException。所以非空路径仍走「快照后迭代」，
 * 只是把快照缓冲区改成复用（@Unique 实例字段），省掉每 tick 的新数组分配。
 *
 * <h2>为什么用反射拿 Timer.update()</h2>
 * {@code DataTimers$Timer} 是<b>包级私有</b>类（javap：{@code class ...$Timer}，
 * 无 public 修饰），从 {@code bin.cnpcplus} 包无法直接引用其类型；
 * 但 {@code update()} 本身是 public。反射查找缓存在实例字段里，只解析一次。
 *
 * <h2>配置</h2>
 * {@code dataTimersOptimize}（默认 true）。关闭时退回「每 tick 新建快照」，
 * 行为与原版完全一致；空表短路保留（原版本来就什么都不做，只是多一次空分配）。
 */
@Mixin(value = DataTimers.class, remap = false)
public class MixinDataTimersUpdate {

    /** 原版的 {@code private Map&lt;Integer, Timer&gt; timers}（CNPC 自有字段，类型擦除后为 Map）。 */
    @Shadow
    private Map timers;

    /** 复用的快照缓冲（非空时替代每 tick 的 {@code new ArrayList}）。 */
    @Unique
    private final List<Object> cnpcplus$buffer = new ArrayList<Object>();

    /** 懒解析的 {@code DataTimers$Timer.update} 句柄。 */
    @Unique
    private Method cnpcplus$timerUpdate;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$fastUpdate(CallbackInfo ci) {
        ci.cancel();
        if (this.timers.isEmpty()) {
            // 原版在这里只是 new 一个空 ArrayList 然后什么都不迭代——纯浪费。
            return;
        }

        List<Object> snapshot;
        if (CnpcPlusConfig.isDataTimersOptimizeEnabled()) {
            this.cnpcplus$buffer.clear();
            snapshot = this.cnpcplus$buffer;
        } else {
            // 与原版完全一致的分配路径。
            snapshot = new ArrayList<Object>();
        }
        snapshot.addAll(this.timers.values());

        if (this.cnpcplus$timerUpdate == null) {
            try {
                this.cnpcplus$timerUpdate = Class
                        .forName("noppes.npcs.entity.data.DataTimers$Timer")
                        .getMethod("update");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("cnpcplus: DataTimers$Timer.update not found", e);
            }
        }

        Iterator<Object> it = snapshot.iterator();
        try {
            while (it.hasNext()) {
                this.cnpcplus$timerUpdate.invoke(it.next());
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("cnpcplus: DataTimers$Timer.update failed", e);
        }
    }
}
