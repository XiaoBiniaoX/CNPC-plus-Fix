package bin.cnpcplus.script;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.ITimers;

/** Per-stack timers advance while the stack is carried by a player. */
public final class ScriptItemTimers implements ITimers {
    private static final class Timer {
        final int interval; final boolean repeating; int remaining;
        Timer(int ticks, boolean repeat) { interval = remaining = ticks; repeating = repeat; }
    }
    private final Map<Integer, Timer> timers = new HashMap<>();
    public void start(int id, int ticks, boolean repeat) {
        if (has(id)) throw new CustomNPCsException("There is already a timer with id: " + id);
        forceStart(id, ticks, repeat);
    }
    public void forceStart(int id, int ticks, boolean repeat) { timers.put(id, new Timer(Math.max(1, ticks), repeat)); }
    public boolean has(int id) { return timers.containsKey(id); }
    public boolean stop(int id) { return timers.remove(id) != null; }
    public void reset(int id) { Timer timer = timers.get(id); if (timer != null) timer.remaining = timer.interval; }
    public void clear() { timers.clear(); }
    public void tick(IntConsumer fire) {
        for (Map.Entry<Integer, Timer> entry : new HashMap<>(timers).entrySet()) {
            Timer timer = entry.getValue();
            if (timers.get(entry.getKey()) != timer || --timer.remaining > 0) continue;
            if (timer.repeating) timer.remaining = timer.interval; else timers.remove(entry.getKey());
            fire.accept(entry.getKey());
        }
    }
}
