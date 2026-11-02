package bin.cnpcplus.respawn;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** NPC-owned deadline; cloned/imported NPCs keep the remaining time, never another identity. */
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
    public static RespawnTimer start(UUID owner, RespawnClock clock, long millis, boolean enabled, long wall) {
        long now = clock.now();
        return new RespawnTimer(owner, clock, now, enabled ? now + toTicks(millis) : wall + Math.max(0, millis), enabled);
    }
    public static RespawnTimer read(NBTTagCompound root, UUID owner, RespawnClock clock, boolean enabled, long wall) {
        if (!root.hasKey(KEY, 10)) return null;
        NBTTagCompound tag = root.getCompoundTag(KEY);
        if (!tag.hasUniqueId("Owner") || !tag.hasUniqueId("ClockId") || !tag.hasKey("Due", 4)
                || !tag.hasKey("DiedAt", 4) || !tag.hasKey("Checkpoint", 4) || !tag.hasKey("GameClock", 1)) return null;
        long died = tag.getLong("DiedAt"), checkpoint = tag.getLong("Checkpoint"), due = tag.getLong("Due");
        boolean game = tag.getBoolean("GameClock");
        if (died < 0 || checkpoint < died || due < 0 || game && due < died) return null;
        if (!owner.equals(tag.getUniqueId("Owner")) || !clock.id().equals(tag.getUniqueId("ClockId")))
            return start(owner, clock, game ? remaining(due, checkpoint) * 50 : remaining(due, wall), enabled, wall);
        clock.restoreFloor(clock.id(), checkpoint);
        RespawnTimer timer = new RespawnTimer(owner, clock, died, due, game); timer.useMode(enabled, wall); return timer;
    }
    public RespawnTimer rebind(UUID owner, RespawnClock clock, boolean enabled, long wall) {
        if (!this.owner.equals(owner) || !this.clock.id().equals(clock.id()))
            return start(owner, clock, gameClock ? remaining(due, this.clock.now()) * 50 : remaining(due, wall), enabled, wall);
        useMode(enabled, wall); return this;
    }
    private void useMode(boolean enabled, long wall) {
        if (enabled == gameClock) return;
        due = enabled ? clock.now() + toTicks(remaining(due, wall)) : wall + remaining(due, clock.now()) * 50;
        gameClock = enabled;
    }
    public boolean ready() { return gameClock && clock.now() >= due; }
    public long wallDeadline() { return due; }
    public void write(NBTTagCompound root) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setUniqueId("Owner", owner); tag.setUniqueId("ClockId", clock.id());
        tag.setLong("DiedAt", diedAt); tag.setLong("Due", due); tag.setLong("Checkpoint", clock.now()); tag.setBoolean("GameClock", gameClock);
        root.setTag(KEY, tag);
    }
    public static long remaining(long due, long now) { return Math.max(0, due - now); }
    private static long toTicks(long millis) { return millis <= 0 ? 0 : millis / 50 + (millis % 50 == 0 ? 0 : 1); }
}
