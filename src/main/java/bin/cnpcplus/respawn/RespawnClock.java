package bin.cnpcplus.respawn;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.data.NbtFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import noppes.npcs.CustomNpcs;

/** Server-thread clock; the existing ordered IO writer receives copied NBT and a path only. */
public final class RespawnClock {
    private static final Map<WorldServer, RespawnClock> CLOCKS = new WeakHashMap<>();
    private final Path file;
    private final UUID id;
    private long ticks;
    private boolean dirty;
    private CompletableFuture<Void> pending;
    private RespawnClock(Path file, UUID id, long ticks) { this.file = file; this.id = id; this.ticks = ticks; }
    public static RespawnClock get(WorldServer world) {
        RespawnClock clock = CLOCKS.get(world);
        if (clock != null) return clock;
        Path file = CustomNpcs.getWorldSaveDirectory().toPath().resolve("data/cnpcplus/respawn-" + world.provider.getDimension() + ".dat");
        try {
            if (Files.exists(file)) {
                NBTTagCompound tag = NbtFiles.read(file);
                if (!tag.hasUniqueId("ClockId") || !tag.hasKey("Ticks", 4) || tag.getLong("Ticks") < 0)
                    throw new IOException("Invalid respawn clock: " + file);
                clock = new RespawnClock(file, tag.getUniqueId("ClockId"), tag.getLong("Ticks"));
            } else { clock = new RespawnClock(file, UUID.randomUUID(), 0); clock.dirty = true; }
        } catch (IOException error) { throw new IllegalStateException("Cannot restore respawn clock; original retained: " + file, error); }
        CLOCKS.put(world, clock); return clock;
    }
    public UUID id() { return id; }
    public long now() { return ticks; }
    public void restoreFloor(UUID clockId, long checkpoint) {
        if (id.equals(clockId) && checkpoint > ticks) { ticks = checkpoint; dirty = true; }
    }
    private void checkWrite() {
        if (pending == null || !pending.isDone()) return;
        try { pending.join(); }
        catch (java.util.concurrent.CompletionException error) { dirty = true; CnpcPlus.LOGGER.error("Respawn clock save failed; will retry: {}", file, error); }
        pending = null;
    }
    private void save() {
        checkWrite(); if (!dirty) return;
        NBTTagCompound snapshot = new NBTTagCompound(); snapshot.setUniqueId("ClockId", id); snapshot.setLong("Ticks", ticks);
        pending = NbtFiles.write(file, snapshot); dirty = false;
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) return;
        RespawnClock clock = get((WorldServer) event.world); clock.checkWrite();
        clock.ticks++; clock.dirty = true;
    }
    @SubscribeEvent public static void save(WorldEvent.Save event) {
        if (event.getWorld() instanceof WorldServer) get((WorldServer) event.getWorld()).save();
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) {
        if (!(event.getWorld() instanceof WorldServer)) return;
        RespawnClock clock = CLOCKS.get(event.getWorld());
        if (clock == null) return;
        clock.save(); NbtFiles.flush(); clock.checkWrite();
        if (clock.dirty) { clock.save(); NbtFiles.flush(); clock.checkWrite(); }
        CLOCKS.remove(event.getWorld());
    }
    public static void clear() { NbtFiles.flush(); CLOCKS.clear(); }
}
