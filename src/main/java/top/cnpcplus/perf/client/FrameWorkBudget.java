package top.cnpcplus.perf.client;

import java.util.function.LongSupplier;

/** 合作式预算：完成当前方块才让出，不跳过索引。 */
public final class FrameWorkBudget {
    private final LongSupplier clock;
    private final long start, nanos;
    private final int max;
    private int work;
    public FrameWorkBudget(long nanos, int max, LongSupplier clock) {
        if (nanos <= 0 || max <= 0) throw new IllegalArgumentException("positive budget required");
        this.nanos = nanos; this.max = max; this.clock = clock; this.start = clock.getAsLong();
    }
    public boolean mayContinue() { return work == 0 || work < max && clock.getAsLong() - start < nanos; }
    public void completedBlock() { work++; }
}
