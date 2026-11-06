package top.cnpcplus.respawn;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.LoggerFactory;

/** Per-dimension monotonic game clock. No entities, chunks or world objects cross the IO boundary. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class RespawnClock extends SavedData {
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cnpcplus-respawn-disk");
        thread.setDaemon(true);
        return thread;
    });
    private final UUID id;
    private long ticks;
    private Future<Boolean> pending;

    public RespawnClock() { this(UUID.randomUUID(), 0); setDirty(); }
    private RespawnClock(UUID id, long ticks) { this.id = id; this.ticks = ticks; }

    public static RespawnClock get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Respawn clock requires server thread");
        return level.getDataStorage().computeIfAbsent(RespawnClock::load, RespawnClock::new, "cnpcplus_respawn_clock");
    }

    public UUID id() { return id; }
    public long now() { checkWrite(); return ticks; }
    public void advance(boolean timeFlows) {
        checkWrite();
        if (timeFlows && ticks < Long.MAX_VALUE) { ticks++; setDirty(); }
    }
    // An entity checkpoint may be newer than the last autosave after a crash. Never move this clock backwards.
    public void restoreFloor(UUID clockId, long checkpoint) {
        if (id.equals(clockId) && checkpoint > ticks) { ticks = checkpoint; setDirty(); }
    }

    public static RespawnClock load(CompoundTag tag) {
        Snapshot snapshot = Snapshot.read(tag);
        return new RespawnClock(snapshot.id(), snapshot.ticks());
    }
    @Override public CompoundTag save(CompoundTag tag) { return new Snapshot(id, ticks).write(tag); }

    @Override public void save(File file) {
        checkWrite();
        if (!isDirty()) return;
        Snapshot snapshot = new Snapshot(id, ticks); // Immutable values; file is captured before leaving the server thread.
        pending = DISK.submit(() -> writeSnapshot(file, snapshot));
        setDirty(false);
    }
    private void checkWrite() {
        if (pending == null || !pending.isDone()) return;
        try { if (!pending.get()) setDirty(); }
        catch (Exception e) { setDirty(); LoggerFactory.getLogger("cnpcplus").error("[respawn] clock save failed", e); }
        pending = null;
    }

    // Check final failures on the server thread and retry even if time is frozen and no more ticks will run.
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        awaitDiskPending();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            RespawnClock clock = get(level);
            clock.checkWrite();
            level.getDataStorage().save();
        }
    }

    public record Snapshot(UUID id, long ticks) {
        public Snapshot {
            if (id == null || ticks < 0) throw new IllegalArgumentException("Invalid respawn clock snapshot");
        }
        CompoundTag write(CompoundTag tag) { tag.putUUID("ClockId", id); tag.putLong("Ticks", ticks); return tag; }
        static Snapshot read(CompoundTag tag) {
            if (!tag.hasUUID("ClockId") || !tag.contains("Ticks", net.minecraft.nbt.Tag.TAG_LONG))
                throw new IllegalArgumentException("Missing respawn clock checkpoint");
            return new Snapshot(tag.getUUID("ClockId"), tag.getLong("Ticks"));
        }
    }

    static boolean writeSnapshot(File file, Snapshot snapshot) {
        java.nio.file.Path temporary = null;
        try {
            if (file.exists()) {
                Snapshot old = Snapshot.read(NbtIo.readCompressed(file).getCompound("data"));
                if (!old.id().equals(snapshot.id()) || old.ticks() > snapshot.ticks())
                    throw new IOException("Refusing to replace another or newer clock");
            }
            var destination = file.toPath().toAbsolutePath();
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), "cnpcplus-respawn-", ".tmp");
            CompoundTag root = new CompoundTag();
            root.put("data", snapshot.write(new CompoundTag()));
            NbtIo.writeCompressed(root, temporary.toFile());
            if (!Snapshot.read(NbtIo.readCompressed(temporary.toFile()).getCompound("data")).equals(snapshot))
                throw new IOException("Clock verification failed");
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception e) {
            LoggerFactory.getLogger("cnpcplus").error("[respawn] clock write failed; original retained: {}", file, e);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    public static void awaitDiskPending() {
        try { DISK.submit(() -> {}).get(30, TimeUnit.SECONDS); }
        catch (Exception e) { LoggerFactory.getLogger("cnpcplus").error("[respawn] waiting for clock IO failed", e); }
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level)
            get(level).advance(true);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { awaitDiskPending(); }
}
