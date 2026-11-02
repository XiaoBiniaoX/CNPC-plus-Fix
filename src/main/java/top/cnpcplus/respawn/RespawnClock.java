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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.LoggerFactory;

/** Per-dimension monotonic game clock. Only immutable values and a captured file cross the IO boundary. */
@EventBusSubscriber(modid = "cnpcplus")
public final class RespawnClock extends SavedData {
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cnpcplus-respawn-disk"); thread.setDaemon(true); return thread;
    });
    private static final Factory<RespawnClock> FACTORY = new Factory<>(RespawnClock::new, (tag, lookup) -> load(tag));
    private final UUID id;
    private long ticks;
    private Future<Boolean> pending;

    public RespawnClock() { this(UUID.randomUUID(), 0); setDirty(); }
    private RespawnClock(UUID id, long ticks) { this.id = id; this.ticks = ticks; }
    public static RespawnClock get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Respawn clock requires server thread");
        return level.getDataStorage().computeIfAbsent(FACTORY, "cnpcplus_respawn_clock");
    }
    public UUID id() { return id; }
    public long now() { checkWrite(); return ticks; }
    public void advance(boolean timeFlows) {
        checkWrite();
        if (timeFlows && ticks < Long.MAX_VALUE) { ticks++; setDirty(); }
    }
    public void restoreFloor(UUID clockId, long checkpoint) {
        if (id.equals(clockId) && checkpoint > ticks) { ticks = checkpoint; setDirty(); }
    }
    public static RespawnClock load(CompoundTag tag) {
        Snapshot snapshot = Snapshot.read(tag);
        return new RespawnClock(snapshot.id(), snapshot.ticks());
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        return new Snapshot(id, ticks).write(tag);
    }
    @Override public void save(File file, HolderLookup.Provider lookup) {
        checkWrite();
        if (!isDirty() || pending != null) return; // Keep dirty until an outstanding write can be checked.
        Snapshot snapshot = new Snapshot(id, ticks);
        pending = DISK.submit(() -> writeSnapshot(file, snapshot));
        setDirty(false);
    }
    private void checkWrite() {
        if (pending == null || !pending.isDone()) return;
        try { if (!pending.get()) setDirty(); }
        catch (Exception e) { setDirty(); LoggerFactory.getLogger("cnpcplus").error("[respawn] clock save failed", e); }
        pending = null;
    }

    public record Snapshot(UUID id, long ticks) {
        public Snapshot { if (id == null || ticks < 0) throw new IllegalArgumentException("Invalid respawn clock snapshot"); }
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
            var destination = file.toPath().toAbsolutePath();
            if (Files.exists(destination)) {
                Snapshot old = Snapshot.read(NbtIo.readCompressed(destination, NbtAccounter.unlimitedHeap()).getCompound("data"));
                if (!old.id().equals(snapshot.id()) || old.ticks() > snapshot.ticks())
                    throw new IOException("Refusing to replace another or newer clock");
            }
            Files.createDirectories(destination.getParent());
            temporary = Files.createTempFile(destination.getParent(), "cnpcplus-respawn-", ".tmp");
            CompoundTag root = new CompoundTag();
            root.put("data", snapshot.write(new CompoundTag()));
            NbtUtils.addCurrentDataVersion(root);
            NbtIo.writeCompressed(root, temporary);
            if (!Snapshot.read(NbtIo.readCompressed(temporary, NbtAccounter.unlimitedHeap()).getCompound("data")).equals(snapshot))
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
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level)
            get(level).advance(level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        awaitDiskPending();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            RespawnClock clock = get(level); clock.checkWrite(); level.getDataStorage().save();
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { awaitDiskPending(); }
}
