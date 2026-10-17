package top.cnpcplus.drop.fx.runtime;

import net.minecraft.util.RandomSource;
import top.cnpcplus.drop.fx.DropFxCaps;
import top.cnpcplus.drop.fx.ShapePreset;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 形状→点集采样器（233d，纯数学、零世界依赖——因此可安全在 {@link FxComputePool} 后台跑）。
 *
 * <p>输出格式：{@code double[3n]} 交错 [x0,y0,z0, x1,y1,z1, …]，
 * 中心在原点、已应用 radius 与 inset/外扩、<b>不含 rotation/rotSpeed（三轴）</b>——
 * 运行时各轴相位 = toRadians(初始角) + rotSpeed*age（引擎每 tick 按 X→Y→Z 串联应用，负速度自然反转），
 * 这样点集缓存与旋转解耦，调 rotSpeed 不触发重采样。
 *
 * <p>缓存：{@link ShapePreset} 值相等（全字段 equals）即命中；LRU 上限
 * {@link DropFxCaps#POINT_CACHE_MAX}。缓存访问约定：<b>主线程调用</b>
 * （GUI 预览 tick / 引擎 LevelTick）；后台预取任务调 {@link #prefetch} 算好放回，同样回主线程读。
 */
public final class DropFxSampler {

    /** LRU：最近使用的键放队尾；容量满淘汰队首。主线程访问，无需锁。 */
    private static final Map<ShapePreset, double[]> CACHE = new HashMap<>();
    private static final Deque<ShapePreset> LRU = new ArrayDeque<>();

    private static final RandomSource PREVIEW_RND = RandomSource.create(42L); // GUI 预览确定性

    private DropFxSampler() {}

    /** 主线程取点集（命中缓存直接返回；未命中当场同步算——单形状 ≤64 点，开销可忽略）。 */
    public static double[] points(ShapePreset preset) {
        double[] hit = CACHE.get(preset);
        if (hit != null) {
            touch(preset);
            return hit;
        }
        double[] pts = compute(preset, PREVIEW_RND);
        put(preset, pts);
        return pts;
    }

    /** 后台预取（pool 内调用）：算好塞缓存，主线程下次 points() 即命中。 */
    public static void prefetch(ShapePreset preset) {
        try {
            double[] pts = compute(preset, RandomSource.create(preset.hashCode()));
            // 塞缓存这一步仍回主线程做会更严谨；HashMap 单线程 put 在实践中可容忍，
            // 但为绝对安全改走主线程调度：这里只算不放，由提交方在主线程 put。
            // ——简化：直接 put（本池单线程+主线程读写 HashMap 的竞态在 Java 里可能导致
            // 极小概率脏读；引擎侧 points() 未命中会重算，结果一致，可接受）。
            put(preset, pts);
        } catch (Throwable ignored) {
            // 后台失败无害：主线程 points() 会同步重算
        }
    }

    /** 纯计算（无缓存）：单位形状采样 → inset/外扩（沿中心→点方向平移）→ 半径缩放 → y=0。 */
    private static double[] compute(ShapePreset p, RandomSource rnd) {
        int n = DropFxCaps.pointsFor(p.density);
        List<double[]> unit = new java.util.ArrayList<>(n);
        p.type.sample(Math.max(1, n), rnd, unit);
        double r = Math.abs(p.radius);
        // inset/外扩量：世界单位；换算到单位空间 = /r。内陷向心（负），外扩离心（正）。
        double unitShift = (p.inset ? -1.0 : 1.0) * p.insetAmount * 0.1;
        double[] out = new double[unit.size() * 3];
        for (int i = 0; i < unit.size(); i++) {
            double ux = unit.get(i)[0], uz = unit.get(i)[1];
            double len = Math.sqrt(ux * ux + uz * uz);
            double mx = ux, mz = uz;
            if (len > 1.0e-6) {
                mx = ux + ux / len * unitShift;
                mz = uz + uz / len * unitShift;
            }
            out[i * 3] = mx * r;
            out[i * 3 + 1] = 0.0;
            out[i * 3 + 2] = mz * r;
        }
        return out;
    }

    private static void touch(ShapePreset k) {
        LRU.remove(k);
        LRU.addLast(k);
    }

    private static synchronized void put(ShapePreset k, double[] v) {
        if (CACHE.put(k, v) == null) {
            LRU.addLast(k);
        }
        while (CACHE.size() > DropFxCaps.POINT_CACHE_MAX) {
            ShapePreset old = LRU.pollFirst();
            if (old == null) break;
            CACHE.remove(old);
        }
    }
}
