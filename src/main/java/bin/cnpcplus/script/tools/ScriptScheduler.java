package bin.cnpcplus.script.tools;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptScheduler {
    private static final Map<Long, TaskHandle> ACTIVE = new LinkedHashMap<>();
    private static long clock, lastId;
    public TaskHandle runLater(int delayTicks, Runnable task) { return schedule(delayTicks, 0, false, task, ScriptScope.current()); }
    public TaskHandle runLater(bin.cnpcplus.api.script.ScriptScope scope, int delayTicks, Runnable task) {
        return schedule(delayTicks, 0, false, task, scope);
    }
    public TaskHandle runRepeating(int initialDelayTicks, int intervalTicks, Runnable task) {
        if (intervalTicks < 1) throw new IllegalArgumentException("Interval must be positive");
        return schedule(initialDelayTicks, intervalTicks, true, task, ScriptScope.current());
    }
    public TaskHandle runRepeating(bin.cnpcplus.api.script.ScriptScope scope, int initialDelayTicks, int intervalTicks, Runnable task) {
        if (intervalTicks < 1) throw new IllegalArgumentException("Interval must be positive");
        return schedule(initialDelayTicks, intervalTicks, true, task, scope);
    }
    public TaskHandle runWithDelay(int initialDelayTicks, int delayTicks, Runnable task) {
        if (delayTicks < 1) throw new IllegalArgumentException("Delay must be positive");
        return schedule(initialDelayTicks, delayTicks, false, task, ScriptScope.current());
    }
    public TaskHandle runWithDelay(bin.cnpcplus.api.script.ScriptScope scope, int initialDelayTicks, int delayTicks, Runnable task) {
        if (delayTicks < 1) throw new IllegalArgumentException("Delay must be positive");
        return schedule(initialDelayTicks, delayTicks, false, task, scope);
    }
    public boolean cancel(TaskHandle handle) { return handle.cancel(); }
    public boolean pause(TaskHandle handle) { return handle.pause(); }
    public boolean resume(TaskHandle handle) { return handle.resume(); }
    public TaskGroup createGroup() { ScriptWorld.server(); return new TaskGroup(ScriptScope.current()); }
    public TaskGroup createGroup(Object owner) {
        ScriptWorld.server(); BooleanSupplier script = ScriptScope.current(), lifetime = ScriptScope.owner(owner);
        return new TaskGroup(ScriptScope.withLifetime(script, lifetime));
    }
    public TaskGroup createGlobalGroup() { ScriptWorld.server(); return new TaskGroup(() -> true); }
    private static TaskHandle schedule(int delay, int period, boolean fixedRate, Runnable action, BooleanSupplier owner) {
        return schedule(delay, period, fixedRate, action, owner, null);
    }
    private static TaskHandle schedule(int delay, int period, boolean fixedRate, Runnable action,
            bin.cnpcplus.api.script.ScriptScope publicScope) {
        Objects.requireNonNull(publicScope, "scope");
        if (!publicScope.isAlive()) throw new IllegalStateException("Script scope is no longer active");
        return schedule(delay, period, fixedRate, action, (BooleanSupplier) publicScope::isAlive, publicScope);
    }
    private static TaskHandle schedule(int delay, int period, boolean fixedRate, Runnable action, BooleanSupplier owner,
            bin.cnpcplus.api.script.ScriptScope publicScope) {
        ScriptWorld.server();
        if (delay < 0) throw new IllegalArgumentException("Delay must be nonnegative");
        TaskHandle handle = new TaskHandle(++lastId, clock + Math.max(1, delay), period, fixedRate, Objects.requireNonNull(action), owner, publicScope);
        if (ACTIVE.size() >= 1024) handle.fail("TASK_REJECTED"); else {
            ACTIVE.put(handle.id, handle);
            if (publicScope != null) try { publicScope.manage(handle); }
            catch (RuntimeException failure) { handle.finish("CANCELLED"); throw failure; }
        }
        return handle;
    }
    public static final class TaskGroup {
        private final BooleanSupplier owner; private boolean cancelled;
        private TaskGroup(BooleanSupplier owner) { this.owner = owner; }
        public TaskHandle runLater(int delayTicks, Runnable task) { return schedule(delayTicks, 0, false, task, ScriptScope.withLifetime(owner, () -> !cancelled)); }
        public TaskHandle runRepeating(int initialDelayTicks, int intervalTicks, Runnable task) {
            if (intervalTicks < 1) throw new IllegalArgumentException("Interval must be positive");
            return schedule(initialDelayTicks, intervalTicks, true, task, ScriptScope.withLifetime(owner, () -> !cancelled));
        }
        public void cancel() { ScriptWorld.server(); cancelled = true; }
    }
    public static final class TaskHandle implements AutoCloseable {
        private final long id; private final int period; private final boolean fixedRate;
        private String state = "SCHEDULED"; private long next, last = -1, pauseDelay;
        private Runnable action; private BooleanSupplier owner; private OperationResult.Diagnostic failure;
        private bin.cnpcplus.api.script.ScriptScope publicScope;
        private TaskHandle(long id, long next, int period, boolean fixedRate, Runnable action, BooleanSupplier owner,
                bin.cnpcplus.api.script.ScriptScope publicScope) {
            this.id = id; this.next = next; this.period = period; this.fixedRate = fixedRate; this.action = action; this.owner = owner;
            this.publicScope = publicScope;
        }
        public long getId() { return id; }
        public String getState() { ScriptWorld.server(); return state; }
        public long getNextRunTime() { return "SCHEDULED".equals(getState()) ? next : -1; }
        public long getLastRunTime() { ScriptWorld.server(); return last; }
        public OperationResult.Diagnostic getFailure() { ScriptWorld.server(); return failure; }
        public boolean isCancelled() { return "CANCELLED".equals(getState()); }
        public boolean isDone() { return Arrays.asList("COMPLETED", "CANCELLED", "FAILED").contains(getState()); }
        public boolean cancel() { if (isDone()) return false; finish("CANCELLED"); return true; }
        @Override public void close() { if (!isDoneState()) finish("CANCELLED"); }
        private boolean isDoneState() { return "COMPLETED".equals(state) || "CANCELLED".equals(state) || "FAILED".equals(state); }
        private boolean pause() { if (!"SCHEDULED".equals(getState())) return false; pauseDelay = Math.max(1, next - clock); state = "PAUSED"; return true; }
        private boolean resume() { if (!"PAUSED".equals(getState())) return false; next = clock + pauseDelay; state = "SCHEDULED"; return true; }
        private void finish(String result) { state = result; action = null; owner = null; publicScope = null; ACTIVE.remove(id); }
        private void fail(String reason) { failure = OperationResult.failure("SCHEDULER", reason).getDiagnostic(); finish("FAILED"); }
    }
    static int taskCount() { ScriptWorld.server(); return ACTIVE.size(); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        clock++;
        for (TaskHandle handle : new ArrayList<>(ACTIVE.values())) {
            if (handle.action == null) continue;
            if (!handle.owner.getAsBoolean()) { handle.finish("CANCELLED"); continue; }
            if (!handle.state.equals("SCHEDULED") || clock < handle.next) continue;
            handle.state = "RUNNING"; handle.last = clock;
            try {
                if (handle.publicScope == null) ScriptScope.callback(handle.owner, handle.action);
                else bin.cnpcplus.script.ScriptRuntimeHost.runCallback(handle.publicScope, handle.action);
            }
            catch (RuntimeException error) {
                handle.fail("CALLBACK_FAILED"); bin.cnpcplus.CnpcPlus.LOGGER.warn("Scheduled script task {} failed", handle.id, error); continue;
            }
            if (!handle.state.equals("RUNNING")) continue;
            if (handle.period == 0) handle.finish("COMPLETED");
            else { handle.next = handle.fixedRate ? handle.next + handle.period : clock + handle.period; handle.state = "SCHEDULED"; }
        }
    }
    public static void stop() { for (TaskHandle handle : new ArrayList<>(ACTIVE.values())) handle.finish("CANCELLED"); clock = 0; }
}
