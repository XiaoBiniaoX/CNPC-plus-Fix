package top.cnpcplus.respawn;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** NPC-owned deadline; config changes convert only the remaining duration, never restart a countdown. */
public final class RespawnTimer {
    public static final String KEY = "CNPCPlusRespawn";
    private final UUID owner;
    private final RespawnClock clock;
    private final long diedAt;
    private long due;
    private boolean gameClock;

    private RespawnTimer(UUID owner, RespawnClock clock, long diedAt, long due, boolean gameClock) {
        this.owner = owner; this.clock = clock; this.diedAt = diedAt; this.due = due; this.gameClock = gameClock;
    }

    public static RespawnTimer start(UUID owner, RespawnClock clock, long remainingMillis, boolean enabled, long wallNow) {
        long now = clock.now();
        return new RespawnTimer(owner, clock, now,
                enabled ? add(now, toTicks(remainingMillis)) : add(wallNow, Math.max(0, remainingMillis)), enabled);
    }

    public static RespawnTimer read(CompoundTag root, UUID owner, RespawnClock clock, boolean enabled, long wallNow) {
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(KEY);
        if (!tag.hasUUID("Owner") || !tag.hasUUID("ClockId") || !tag.contains("Due", Tag.TAG_LONG)
                || !tag.contains("DiedAt", Tag.TAG_LONG) || !tag.contains("Checkpoint", Tag.TAG_LONG)
                || !tag.contains("GameClock", Tag.TAG_BYTE)) return null;
        UUID clockId = tag.getUUID("ClockId");
        long died = tag.getLong("DiedAt"), checkpoint = tag.getLong("Checkpoint"), due = tag.getLong("Due");
        boolean game = tag.getBoolean("GameClock");
        if (died < 0 || checkpoint < died || due < 0 || game && due < died) return null;
        if (!owner.equals(tag.getUUID("Owner")) || !clock.id().equals(clockId)) {
            // Clones and imported worlds keep an independent remaining duration, not another NPC's absolute deadline.
            return start(owner, clock, game ? toMillis(remaining(due, checkpoint)) : remaining(due, wallNow), enabled, wallNow);
        }
        clock.restoreFloor(clockId, checkpoint);
        RespawnTimer timer = new RespawnTimer(owner, clock, died, due, game);
        timer.useMode(enabled, clock.now(), wallNow);
        return timer;
    }

    public RespawnTimer rebind(UUID currentOwner, RespawnClock currentClock, boolean enabled, long wallNow) {
        if (!owner.equals(currentOwner) || !clock.id().equals(currentClock.id()))
            return start(currentOwner, currentClock, gameClock ? toMillis(remaining(due, clock.now())) : remaining(due, wallNow), enabled, wallNow);
        useMode(enabled, currentClock.now(), wallNow);
        return this;
    }

    public void useMode(boolean enabled, long tickNow, long wallNow) {
        if (enabled == gameClock) return;
        due = enabled ? add(tickNow, toTicks(remaining(due, wallNow))) : add(wallNow, toMillis(remaining(due, tickNow)));
        gameClock = enabled;
    }
    public boolean ready(long tickNow) { return gameClock && tickNow >= due; }
    public long wallDeadline() { return due; }
    public void write(CompoundTag root, long tickNow) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner); tag.putUUID("ClockId", clock.id());
        tag.putLong("DiedAt", diedAt); tag.putLong("Due", due); tag.putLong("Checkpoint", tickNow);
        tag.putBoolean("GameClock", gameClock);
        root.put(KEY, tag);
    }
    public static long remaining(long due, long now) { return due <= now ? 0 : due - now; }
    private static long add(long a, long b) { return b > Long.MAX_VALUE - a ? Long.MAX_VALUE : a + b; }
    private static long toTicks(long millis) { return millis <= 0 ? 0 : millis / 50 + (millis % 50 == 0 ? 0 : 1); }
    private static long toMillis(long ticks) { return ticks > Long.MAX_VALUE / 50 ? Long.MAX_VALUE : ticks * 50; }
}
