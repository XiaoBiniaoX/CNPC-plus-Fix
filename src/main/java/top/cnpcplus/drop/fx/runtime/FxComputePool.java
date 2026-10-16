package top.cnpcplus.drop.fx.runtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 粒子框架的后台计算线程池（233d「安全异步」的执行载体）。
 *
 * <p><b>线程模型铁律</b>（整个 fx 框架唯一允许的跨线程边界）：
 * <ul>
 *   <li>池内只跑<b>纯数学</b>（形状采样等）——禁止碰 world/entity/registry/网络；</li>
 *   <li>输入是不可变参数副本，输出是不可变数组快照；</li>
 *   <li>结果经 {@code CompletableFuture} 交回主线程消费
 *       （服务端在 LevelTick、客户端在 GUI tick）；</li>
 *   <li>单线程：采样计算量小（≤64 点×25 形状），单线程足够且天然免锁。</li>
 * </ul>
 * daemon 线程：JVM 退出不被本池拖住。
 */
public final class FxComputePool {

    private static final AtomicInteger THREAD_NO = new AtomicInteger();

    private static volatile ExecutorService pool;

    private FxComputePool() {}

    public static ExecutorService get() {
        ExecutorService p = pool;
        if (p == null) {
            synchronized (FxComputePool.class) {
                p = pool;
                if (p == null) {
                    p = pool = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "cnpcplus-drop-fx-" + THREAD_NO.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    });
                }
            }
        }
        return p;
    }

    /** 提交纯数学任务（异常在池内吞掉打日志，不炸主线程）。 */
    public static void submit(Runnable task) {
        get().execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger("cnpcplus")
                        .error("[drop-fx] 后台采样任务异常", t);
            }
        });
    }
}
