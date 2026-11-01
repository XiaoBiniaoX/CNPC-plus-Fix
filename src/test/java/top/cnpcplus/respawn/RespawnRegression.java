package top.cnpcplus.respawn;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import top.cnpcplus.config.CnpcPlusServerConfig;

/** Real timer and verified asynchronous clock IO; no entities/world thread or runtime Mixin fixture. */
public final class RespawnRegression {
    public static void main(String[] args) throws Exception {
        check(CnpcPlusServerConfig.IndependentRespawnClock.getDefault(), "clock disabled by default");
        RespawnClock clock = new RespawnClock();
        check(clock.isDirty(), "new frozen clock must be persisted");
        UUID npc = UUID.randomUUID();
        RespawnTimer timer = RespawnTimer.start(npc, clock, 2000, true, 1000);
        for (int i = 0; i < 20; i++) clock.advance(true);
        check(!timer.ready(clock.now()), "respawned before deadline");
        for (int i = 0; i < 4000; i++) clock.advance(false);
        check(clock.now() == 20 && !timer.ready(clock.now()), "frozen time progressed");
        CompoundTag npcTag = new CompoundTag();
        timer.write(npcTag, clock.now());
        check(npcTag.getCompound(RespawnTimer.KEY).getLong("DiedAt") == 0, "death timestamp missing");
        RespawnClock reloaded = RespawnClock.load(clock.save(new CompoundTag()));
        RespawnTimer loaded = RespawnTimer.read(npcTag, npc, reloaded, true, Long.MAX_VALUE / 2);
        check(loaded != null && !loaded.ready(reloaded.now()), "offline wall time completed game timer");
        for (int i = 0; i < 20; i++) reloaded.advance(true); // NPC remains unloaded while world clock advances.
        loaded = RespawnTimer.read(npcTag, npc, reloaded, true, 9000000);
        check(loaded.ready(reloaded.now()), "loaded NPC did not observe elapsed clock");
        timer.useMode(false, clock.now(), 10000);
        check(timer.wallDeadline() == 11000, "disable restarted countdown instead of converting remaining time");
        timer.useMode(true, clock.now(), 10500);
        check(!timer.ready(29) && timer.ready(30), "enable did not preserve remaining duration");
        timer.write(npcTag, clock.now());
        RespawnTimer clone = RespawnTimer.read(npcTag, UUID.randomUUID(), clock, true, 10500);
        check(clone != null && !clone.ready(29) && clone.ready(30), "cloned timer not independent");
        clone.useMode(false, clock.now(), 10500);
        check(timer.ready(30), "clone mutation changed source timer");
        RespawnClock anotherWorld = new RespawnClock();
        for (int i = 0; i < 100; i++) anotherWorld.advance(true);
        RespawnTimer imported = RespawnTimer.read(npcTag, npc, anotherWorld, true, 99999999);
        check(!imported.ready(109) && imported.ready(110), "cross-world absolute deadline leaked");
        RespawnTimer moved = timer.rebind(npc, anotherWorld, true, 10500);
        check(moved != timer && !moved.ready(109) && moved.ready(110), "live dimension transfer kept old clock deadline");
        moved = moved.rebind(npc, clock, false, 20000);
        check(moved.wallDeadline() == 20500, "dimension and mode switch restarted remaining duration");
        check(timer.rebind(npc, clock, true, 20000) == timer && timer.ready(30), "same world restarted timer");
        check(timer.rebind(UUID.randomUUID(), clock, true, 20000) != timer, "live owner change shared timer");
        CompoundTag floor = clock.save(new CompoundTag());
        floor.putLong("Ticks", 0);
        RespawnClock crashRecovery = RespawnClock.load(floor);
        RespawnTimer.read(npcTag, npc, crashRecovery, true, 10500);
        check(crashRecovery.now() == 20, "newer entity checkpoint did not restore clock floor");
        check(RespawnTimer.start(npc, clock, 0, true, 0).ready(clock.now()), "zero duration not ready");
        check(RespawnTimer.start(npc, clock, Long.MAX_VALUE, true, 0).ready(Long.MAX_VALUE), "large duration overflow");
        CompoundTag invalid = npcTag.copy(); invalid.getCompound(RespawnTimer.KEY).putLong("Checkpoint", -1);
        check(RespawnTimer.read(invalid, npc, clock, true, 0) == null, "invalid checkpoint accepted");
        invalid = npcTag.copy(); invalid.getCompound(RespawnTimer.KEY).remove("GameClock");
        check(RespawnTimer.read(invalid, npc, clock, true, 0) == null, "missing clock mode silently changed timing");

        Path evidence = Files.createTempDirectory(Path.of(args[0]), "respawn-regression-");
        var file = evidence.resolve("clock.dat").toFile();
        clock.setDirty(); clock.save(file);
        RespawnClock.awaitDiskPending();
        check(NbtIo.readCompressed(file).getCompound("data").equals(clock.save(new CompoundTag())), "async clock save mismatch");
        byte[] before = Files.readAllBytes(file.toPath());
        check(!RespawnClock.writeSnapshot(file, new RespawnClock.Snapshot(UUID.randomUUID(), 999)), "different world clock overwrote file");
        check(!RespawnClock.writeSnapshot(file, new RespawnClock.Snapshot(clock.id(), 0)), "stale clock overwrote newer file");
        check(java.util.Arrays.equals(before, Files.readAllBytes(file.toPath())), "failure altered original clock");
        clock.advance(true); clock.save(file); clock.advance(true); clock.save(file);
        RespawnClock.awaitDiskPending();
        check(NbtIo.readCompressed(file).getCompound("data").getLong("Ticks") == 22, "queued async saves out of order");
        var blockedParent = evidence.resolve("blocked-parent");
        Files.writeString(blockedParent, "isolated failure fixture");
        var blocked = blockedParent.resolve("clock.dat").toFile();
        clock.setDirty(); clock.save(blocked); RespawnClock.awaitDiskPending();
        clock.advance(false); // A failed write must restore dirty state even while time is frozen.
        check(clock.now() == 22 && clock.isDirty(), "frozen clock lost failed save retry");
        var retry = evidence.resolve("retry.dat").toFile();
        clock.save(retry); RespawnClock.awaitDiskPending(); clock.advance(false);
        check(!clock.isDirty() && NbtIo.readCompressed(retry).getCompound("data").getLong("Ticks") == 22, "retry did not persist the same frozen checkpoint");
        check(Files.readString(blockedParent).equals("isolated failure fixture"), "failure damaged the blocking file");
        System.out.println("RESPAWN_REGRESSION=PASS (timer/persistence/IO only; runtime Mixin and world lifecycle separate)");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
