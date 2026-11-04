package top.cnpcplus.persist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Atomic, verified blobs. Inline data survives every failed read/write and portable export. */
public final class NpcDataExternalStore {
    public static final NpcDataExternalStore SPAWNER = new NpcDataExternalStore(
        "spawner", "CNPCPlusSpawnerId", "CNPCPlusSpawnerState",
        java.util.Set.of("SpawnerData")::contains,
        tag -> !tag.getList("SpawnerData", Tag.TAG_COMPOUND).isEmpty()
    );
    public static final NpcDataExternalStore INVENTORY = new NpcDataExternalStore(
        "inventory", "CNPCPlusInvId", "CNPCPlusInvState",
        java.util.Set.of("NpcInv", "DropChance", "Armor", "Weapons", "MinExp", "MaxExp", "LootMode", "TransformInv")::contains,
        tag -> !tag.getList("NpcInv", Tag.TAG_COMPOUND).isEmpty()
            || !tag.getList("DropChance", Tag.TAG_COMPOUND).isEmpty()
            || !tag.getList("Armor", Tag.TAG_COMPOUND).isEmpty()
            || !tag.getList("Weapons", Tag.TAG_COMPOUND).isEmpty()
            || tag.contains("TransformInv", Tag.TAG_COMPOUND)
    );
    public static final NpcDataExternalStore SCRIPTS = new NpcDataExternalStore(
        "scripts", "CNPCPlusScriptsId", "CNPCPlusScriptsMigration",
        java.util.Set.of("Scripts", "ScriptLanguage", "ScriptEnabled")::contains,
        tag -> !tag.getList("Scripts", Tag.TAG_COMPOUND).isEmpty()
    );
    public static final NpcDataExternalStore ADVANCED = new NpcDataExternalStore(
        "advanced", "CNPCPlusAdvancedId", "CNPCPlusAdvancedState",
        key -> java.util.Set.of("NpcLines", "NpcKilledLines", "NpcInteractLines", "NpcAttackLines",
                "NpcKillLines", "NpcInteractNPCLines", "CNPCPlusMeleeLines", "OrderedLines", "NpcIdleSound", "NpcAngrySound",
                "NpcHurtSound", "NpcDeathSound", "NpcStepSound", "DisablePitch", "CNPCPlusSoundArrays", "NPCDialogOptions",
                 "BossBar", "BossColor", "CNPCPlusBossbar", "CNPCPlusAdvanced", "TransformAdvanced", "TransformRole").contains(key)
                || key.startsWith("Mercenary") || key.startsWith("CNPCPlusFollower"),
        tag -> !tag.isEmpty()
    );
    public static final NpcDataExternalStore MODEL = new NpcDataExternalStore(
        "model", "CNPCPlusModelId", "CNPCPlusModelState",
        // Transform snapshots contain ModelData/PuppetBody under native compound roots.
        // Keep each whole snapshot, including type/extension keys, for native transform.load.
        key -> java.util.Set.of("NpcModelData", "TransformDisplay", "TransformJob").contains(key)
                || key.startsWith("Puppet") || key.startsWith("CNPCPlusPuppet"),
        tag -> !tag.isEmpty()
    );

    public static final NpcDataExternalStore RESISTANCES = new NpcDataExternalStore(
        "resistances", "CNPCPlusResistancesId", "CNPCPlusResistancesState",
        "Resistances"::equals, tag -> tag.contains("Resistances", Tag.TAG_COMPOUND)
    );

    private static final int VERIFIED = 2;
    private static final int CLEANED = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcplus");

    private final String logTag;
    private final String idKey;
    private final String stateKey;
    private final Predicate<String> dataKeys;
    private final Predicate<CompoundTag> probe;
    private final Map<Object, State> ACTIVE = new WeakHashMap<>();

    NpcDataExternalStore(String logTag, String idKey, String stateKey, Predicate<String> dataKeys, Predicate<CompoundTag> probe) {
        this.logTag = logTag;
        this.idKey = idKey;
        this.stateKey = stateKey;
        this.dataKeys = dataKeys;
        this.probe = probe;
    }

    private static final class State {
        UUID id;
        UUID owner;
        int migration;
        CompoundTag data = new CompoundTag();
        FileTime modified;
        long length;
    }

    public synchronized boolean prepareLoad(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || npc.level() == null || npc.level().isClientSide) return false;
        return prepareLoad(npc, npc.getUUID(), tag, directory());
    }

    public synchronized boolean prepareEarlyLoad(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || npc.level() == null || npc.level().isClientSide) return false;
        UUID owner = tag.hasUUID("UUID") ? tag.getUUID("UUID") : npc.getUUID();
        return prepareLoad(npc, owner, tag, directory());
    }

    // Package-private entry points exercise the real IO with no Minecraft entity/registry fixture.
    synchronized boolean prepareLoad(Object identity, UUID owner, CompoundTag tag, File directory) {
        ACTIVE.remove(identity);
        UUID id = parseId(tag.getString(idKey));
        if (id == null) id = owner;
        State state = readState(directory, id);
        try {
            if (state != null) {
                if (!verifyData(directory, state)) throw new IOException("store verification failed");
                if (!owner.equals(state.owner)) {
                    state = fork(state, owner);
                }
                // Failed saves and portable/legacy snapshots keep authoritative inline values.
                CompoundTag merged = state.data.copy();
                attachData(merged, extractData(tag));
                // A later migration write can fail: native load must still see the complete snapshot.
                attachData(tag, merged);
                if (!merged.equals(state.data) || !stateFile(directory, state.id).isFile()) {
                    State next = new State();
                    next.id = state.id;
                    next.owner = owner;
                    next.data = merged;
                    persist(directory, next, VERIFIED);
                    state = next;
                }
                attachData(tag, state.data);
                adopt(identity, tag, state);
                return true;
            }
            if (stateFile(directory, id).exists()) throw new IOException("unreadable store; refusing overwrite");
            CompoundTag data = extractData(tag);
            if (!hasData(data)) return false;
            State migrated = new State();
            migrated.id = id;
            migrated.owner = owner;
            migrated.data = data;
            persist(directory, migrated, VERIFIED);
            adopt(identity, tag, migrated);
            return false;
        } catch (Exception e) {
            ACTIVE.remove(identity);
            LOGGER.error("[{}] migration failed; NBT retained for {}", logTag, owner, e);
            return false;
        }
    }

    public synchronized void saveNpc(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || npc.level() == null || npc.level().isClientSide) return;
        saveNpc(npc, npc.getUUID(), tag, directory());
    }

    synchronized void saveNpc(Object identity, UUID owner, CompoundTag tag, File directory) {
        State active = ACTIVE.get(identity);
        UUID requested = parseId(tag.getString(idKey));
        UUID id = requested != null ? requested : active != null ? active.id : owner;
        CompoundTag data = extractData(tag);
        State state = active != null && active.id.equals(id) ? active : readState(directory, id);
        try {
            if (state == active && state != null && !unchanged(directory, state)) {
                state = readState(directory, id);
                if (state == null) throw new IOException("active store changed or disappeared; NBT retained");
            }
            if (state == null && stateFile(directory, id).exists()) throw new IOException("unreadable store; refusing overwrite");
            if (state != null && !owner.equals(state.owner)) {
                state = fork(state, owner);
                // Never discard missing data on an uninitialized clone.
                if (!hasData(data)) {
                    attachData(tag, state.data);
                    data = extractData(tag);
                }
            }
            if (!hasData(data)) {
                if (state == null) return;
                if (hasData(state.data) && active == null) {
                    attachData(tag, state.data);
                    LOGGER.error("[{}] in-memory data empty; external store kept for {}", logTag, owner);
                    return;
                }
            }
            if (state != null && state.data.equals(data) && stateFile(directory, state.id).isFile()) {
                adopt(identity, tag, state);
                stripData(tag);
                return;
            }
            State next = new State();
            next.id = state == null ? id : state.id;
            next.owner = owner;
            next.data = data;
            persist(directory, next, CLEANED);
            // Nothing capable of IO failure follows NBT stripping.
            adopt(identity, tag, next);
            stripData(tag);
        } catch (Exception e) {
            LOGGER.error("[{}] save failed; NBT retained for {}", logTag, owner, e);
        }
    }

    private void adopt(Object identity, CompoundTag tag, State state) {
        ACTIVE.put(identity, state);
        tag.putString(idKey, state.id.toString());
        tag.putInt(stateKey, state.migration);
    }

    private void attachData(CompoundTag tag, CompoundTag data) {
        for (String key : data.getAllKeys()) {
            if (isDataKey(key)) tag.put(key, data.get(key).copy());
        }
    }

    private CompoundTag extractData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        for (String key : tag.getAllKeys()) {
            if (isDataKey(key)) data.put(key, tag.get(key).copy());
        }
        return data;
    }

    private void stripData(CompoundTag tag) {
        for (String key : java.util.Set.copyOf(tag.getAllKeys())) if (isDataKey(key)) tag.remove(key);
    }

    private boolean isDataKey(String key) {
        return !key.equals(idKey) && !key.equals(stateKey) && dataKeys.test(key);
    }

    private boolean hasData(CompoundTag tag) {
        return probe.test(tag);
    }

    private boolean verifyData(File directory, State expected) {
        State reread = readState(directory, expected.id);
        return reread != null && expected.owner.equals(reread.owner) && expected.data.equals(reread.data);
    }

    private static UUID parseId(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    private File directory() {
        return new File(CustomNpcs.getLevelSaveDirectory(), "data/cnpcplus/" + logTag);
    }

    private static File stateFile(File directory, UUID id) {
        return new File(directory, id + ".dat");
    }

    /** Detached preview read: no migration, caching, writes or reference removal. Inline values win. */
    public void readPreview(CompoundTag tag, File worldDirectory) throws IOException {
        String raw = tag.getString(idKey);
        if (raw.isEmpty()) return;
        UUID id = parseId(raw);
        if (id == null || !id.toString().equalsIgnoreCase(raw) || worldDirectory == null) throw new IOException("invalid preview store reference");
        var world = worldDirectory.toPath().toRealPath();
        var directory = world.resolve("data/cnpcplus/" + logTag).toRealPath();
        var file = stateFile(directory.toFile(), id).toPath().toRealPath();
        if (!directory.startsWith(world) || !file.getParent().equals(directory)
                || Files.size(file) > 8 * 1024 * 1024) throw new IOException("preview store outside world or too large");
        try (var input = Files.newInputStream(file);
             var gzip = new java.util.zip.GZIPInputStream(com.google.common.io.ByteStreams.limit(input, 8 * 1024 * 1024));
             var data = new java.io.DataInputStream(com.google.common.io.ByteStreams.limit(gzip, 8 * 1024 * 1024))) {
            CompoundTag root = NbtIo.read(data, new net.minecraft.nbt.NbtAccounter(8 * 1024 * 1024));
            if (!root.contains("Data", Tag.TAG_COMPOUND)) throw new IOException("missing preview store Data");
            for (String key : root.getCompound("Data").getAllKeys()) {
                if (isDataKey(key) && !tag.contains(key)) tag.put(key, root.getCompound("Data").get(key).copy());
            }
        }
    }

    private State readState(File directory, UUID id) {
        File file = stateFile(directory, id);
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(input);
            if (!root.contains("Data", Tag.TAG_COMPOUND)) throw new IOException("missing Data compound");
            State state = new State();
            state.id = id;
            state.owner = UUID.fromString(root.getString("Owner"));
            state.migration = root.getInt("MigrationState");
            state.data = root.getCompound("Data");
            state.modified = Files.getLastModifiedTime(file.toPath());
            state.length = file.length();
            return state;
        } catch (Exception e) {
            LOGGER.error("[{}] store read failed for {}", logTag, id, e);
            return null;
        }
    }

    private void writeState(File directory, State state) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putString("Owner", state.owner.toString());
        root.putInt("MigrationState", state.migration);
        root.put("Data", state.data.copy());
        writeCompressed(stateFile(directory, state.id), root);
    }

    private static State fork(State source, UUID owner) {
        State copy = new State();
        copy.id = UUID.randomUUID();
        copy.owner = owner;
        copy.data = source.data.copy();
        return copy;
    }

    private void persist(File directory, State state, int migration) throws IOException {
        state.migration = migration;
        writeState(directory, state);
        if (!verifyData(directory, state)) throw new IOException("external data verification failed");
        File file = stateFile(directory, state.id);
        state.modified = Files.getLastModifiedTime(file.toPath());
        state.length = file.length();
    }

    private static boolean unchanged(File directory, State state) throws IOException {
        File file = stateFile(directory, state.id);
        return file.isFile() && state.length == file.length()
                && state.modified != null && state.modified.equals(Files.getLastModifiedTime(file.toPath()));
    }

    /** Materialize before cleanTags deletes UUID. On failure retain the reference and all inline keys. */
    public synchronized boolean makePortable(CompoundTag tag) {
        UUID id = parseId(tag.getString(idKey));
        return id == null ? tag.getString(idKey).isEmpty() : materialize(tag, directory(), id);
    }

    synchronized boolean makePortable(CompoundTag tag, File directory) {
        UUID id = parseId(tag.getString(idKey));
        return id == null ? tag.getString(idKey).isEmpty() : materialize(tag, directory, id);
    }

    private boolean materialize(CompoundTag tag, File directory, UUID id) {
        State state = readState(directory, id);
        if (state == null || !verifyData(directory, state)) {
            LOGGER.error("[{}] cannot materialize clone {}; keeping inline NBT/reference", logTag, id);
            return false;
        }
        for (String key : state.data.getAllKeys()) {
            if (isDataKey(key) && !tag.contains(key)) tag.put(key, state.data.get(key).copy());
        }
        tag.remove(idKey);
        tag.remove(stateKey);
        return true;
    }

    public static boolean makeAllPortable(CompoundTag tag) {
        boolean complete = true;
        for (NpcDataExternalStore store : new NpcDataExternalStore[]{SPAWNER, INVENTORY, SCRIPTS, ADVANCED, MODEL, RESISTANCES}) {
            complete &= store.makePortable(tag);
        }
        complete &= top.cnpcplus.trader.MerchantExternalStore.makePortable(tag);
        return complete;
    }

    private static void writeCompressed(File target, CompoundTag tag) throws IOException {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("cannot create " + parent);
        File temp = new File(parent, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) { NbtIo.writeCompressed(tag, output); }
        try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException unsupported) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
}
