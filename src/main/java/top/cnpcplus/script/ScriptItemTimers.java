package top.cnpcplus.script;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.ITimers;

/** A runtime timer table per scripted item stack. Time advances while the stack is carried. */
public final class ScriptItemTimers implements ITimers {
    private static final class Timer {
        final int interval;
        final boolean repeating;
        int remaining;

        Timer(int interval, boolean repeating) {
            this.interval = interval;
            this.repeating = repeating;
            this.remaining = interval;
        }
    }

    private final Map<Integer, Timer> timers = new HashMap<>();

    @Override public void start(int id, int ticks, boolean repeat) {
        if (has(id)) throw new CustomNPCsException("There is already a timer with id: " + id);
        forceStart(id, ticks, repeat);
    }

    @Override public void forceStart(int id, int ticks, boolean repeat) {
        timers.put(id, new Timer(Math.max(1, ticks), repeat));
    }

    @Override public boolean has(int id) { return timers.containsKey(id); }
    @Override public boolean stop(int id) { return timers.remove(id) != null; }
    @Override public void clear() { timers.clear(); }

    @Override public void reset(int id) {
        Timer timer = timers.get(id);
        if (timer == null) throw new CustomNPCsException("There is no timer with id: " + id);
        timer.remaining = timer.interval;
    }

    public void tick(IntConsumer fire) {
        if (timers.isEmpty()) return;
        for (var entry : Map.copyOf(timers).entrySet()) {
            int id = entry.getKey();
            Timer timer = entry.getValue();
            if (timers.get(id) != timer || --timer.remaining > 0) continue;
            if (timer.repeating) timer.remaining = timer.interval;
            else timers.remove(id);
            fire.accept(id);
        }
    }
}
