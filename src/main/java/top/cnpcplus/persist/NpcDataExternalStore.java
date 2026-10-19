package top.cnpcplus.persist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

/**
 * NPC 局部数据键的通用外置存储（240 #2/#3），逻辑逐行照 {@link NpcScriptExternalStore} 模板：
 * 状态机 1-5、稳定 UUID、失败保留旧 NBT、零 IO 快路径、仅 ERROR 日志。
 *
 * <p>两个实例（键集/目录/ID 指针互不相干，各自持有独立 ACTIVE 表）：
 * <ul>
 *   <li>{@link #SPAWNER}：召唤师 {@code SpawnerData}（ListTag），目录 {@code data/cnpcplus/spawner}。</li>
 *   <li>{@link #INVENTORY}：掉落物两键 {@code NpcInv} + {@code DropChance}（均 ListTag），
 *       目录 {@code data/cnpcplus/inventory}；Armor/Weapons/MinExp/MaxExp/LootMode 等仍留 NBT。</li>
 * </ul>
 *
 * <p>与 scripts 的唯一结构性差异是注入时机：prepareLoad 挂在 m_7378_ 内
 * {@code DataScript.load} 的 INVOKE（super 读 UUID 之后、原生 job.load/inventory.load
 * 与 linked loadNpcData 之前）——贴键后原生 load 自然读到，无需补 load 调用；
 * linked 快照仍在其后覆盖（linked 权威顺序不变）。故返回值调用方不消费。
 *
 * <p>失败口径同 scripts：读档文件不可读 → 不动文件不贴键，原生存档 NBT 照常走；
 * 内存无数据且磁盘有数据 → 跳过写盘绝不覆盖；内容与磁盘一致 → 零 IO 快路径
 * （全 NPC 每次世界存档都走这里）；空数据 NPC 不建文件（键留在 NBT，几字节）。
 */
public final class NpcDataExternalStore {
    /** 召唤师 SpawnerData（CloneSpawnData.save 的 ListTag：slot/tab/name 条目）。 */
    public static final NpcDataExternalStore SPAWNER = new NpcDataExternalStore(
        "spawner", "CNPCPlusSpawnerId", "CNPCPlusSpawnerState",
        new String[] { "SpawnerData" },
        tag -> !tag.getList("SpawnerData", Tag.TAG_COMPOUND).isEmpty()
    );

    /** 掉落物：drops（NpcInv）+ 掉落率（DropChance）。任一非空即算有数据。 */
    public static final NpcDataExternalStore INVENTORY = new NpcDataExternalStore(
        "inventory", "CNPCPlusInvId", "CNPCPlusInvState",
        new String[] { "NpcInv", "DropChance" },
        tag -> !tag.getList("NpcInv", Tag.TAG_COMPOUND).isEmpty()
            || !tag.getList("DropChance", Tag.TAG_COMPOUND).isEmpty()
    );

    private static final int COPYING = 1;
    private static final int VERIFIED = 2;
    private static final int EXTERNAL_ACTIVE = 3;
    private static final int CLEANUP_PENDING = 4;
    private static final int CLEANED = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcplus");

    private final String logTag;
    private final String idKey;
    private final String stateKey;
    private final String[] dataKeys;
    private final Predicate<CompoundTag> probe;
    private final Map<EntityNPCInterface, State> ACTIVE = new WeakHashMap<>();

    private NpcDataExternalStore(String logTag, String idKey, String stateKey, String[] dataKeys, Predicate<CompoundTag> probe) {
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
    }

    /**
     * EntityNPCInterface.m_7378_ 内 DataScript.load INVOKE 调用（super 已读 UUID、
     * 原生 job.load/inventory.load 尚未跑）。贴键即由原生 load 读走；返回 true 表示
     * 外置文件贴入，false 表示原生 NBT 已够用（含 legacy 内联迁移场景）。
     */
    public synchronized boolean prepareLoad(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || npc.level() == null || npc.level().isClientSide) return false;
        UUID owner = npc.getUUID();
        UUID id = parseId(tag.getString(idKey));
        if (id == null) id = owner;
        State state = readState(id);
        boolean legacy = hasData(tag);

        try {
            if (state != null && !owner.equals(state.owner)) {
                UUID replacement = UUID.randomUUID();
                copyState(state, replacement, owner);
                id = replacement;
                state = readState(id);
            }

            if (state != null && verifyData(state)) {
                ACTIVE.put(npc, state);
                attachData(tag, state.data);
                tag.putString(idKey, state.id.toString());
                tag.putInt(stateKey, state.migration);
                return true;
            }

            if (!legacy) return false;

            State migrated = new State();
            migrated.id = id;
            migrated.owner = owner;
            migrated.data = extractData(tag);
            migrated.migration = COPYING;
            ACTIVE.put(npc, migrated);
            tag.putString(idKey, id.toString());
            tag.putInt(stateKey, COPYING);
            writeState(migrated);
            if (!verifyData(migrated)) throw new IOException("data migration verification failed");
            migrated.migration = VERIFIED;
            writeState(migrated);
            tag.putInt(stateKey, VERIFIED);
            return false;
        } catch (Exception e) {
            ACTIVE.remove(npc);
            LOGGER.error("[{}] migration failed; NBT retained for {}", logTag, owner, e);
            return false;
        }
    }

    /** EntityNPCInterface.m_7380_ RETURN 调用：外置写盘并从实体 NBT 摘除数据键。 */
    public synchronized void saveNpc(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || npc.level() == null || npc.level().isClientSide) return;
        UUID owner = npc.getUUID();
        UUID id = parseId(tag.getString(idKey));
        if (id == null) id = owner;
        CompoundTag data = extractData(tag);
        File file = stateFile(id);
        State state = ACTIVE.get(npc);
        if (state != null && (!owner.equals(state.owner) || !id.equals(state.id))) state = null;
        if (state == null) state = readState(id);
        if (state != null && !owner.equals(state.owner)) state = null;

        try {
            if (!hasData(data)) {
                if (state == null && file.isFile()) {
                    LOGGER.error("[{}] external store unreadable; NBT retained for {}", logTag, owner);
                    return;
                }
                if (state == null) return;
                if (hasData(state.data) && !ACTIVE.containsKey(npc)) {
                    LOGGER.error("[{}] in-memory data empty; external store kept for {}", logTag, owner);
                    return;
                }
                if (state.data.equals(data)) {
                    adopt(npc, tag, state);
                    stripData(tag);
                    return;
                }
            } else if (state != null && state.data.equals(data)) {
                adopt(npc, tag, state);
                stripData(tag);
                return;
            }

            if (state == null) {
                state = new State();
                state.id = id;
                state.owner = owner;
            }
            state.data = data;
            state.migration = COPYING;
            writeState(state);
            if (!verifyData(state)) throw new IOException("data save verification failed");
            state.migration = EXTERNAL_ACTIVE;
            writeState(state);
            tag.putString(idKey, state.id.toString());
            tag.putInt(stateKey, CLEANUP_PENDING);
            stripData(tag);
            state.migration = CLEANED;
            tag.putInt(stateKey, CLEANED);
            writeState(state);
            ACTIVE.put(npc, state);
        } catch (Exception e) {
            LOGGER.error("[{}] save failed; NBT retained for {}", logTag, owner, e);
        }
    }

    private void adopt(EntityNPCInterface npc, CompoundTag tag, State state) {
        ACTIVE.put(npc, state);
        tag.putString(idKey, state.id.toString());
        tag.putInt(stateKey, state.migration);
    }

    private void attachData(CompoundTag tag, CompoundTag data) {
        for (String key : dataKeys) {
            tag.remove(key);
            Tag value = data.get(key);
            if (value != null) tag.put(key, value.copy());
        }
    }

    private CompoundTag extractData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        for (String key : dataKeys) {
            Tag value = tag.get(key);
            if (value != null) data.put(key, value.copy());
        }
        return data;
    }

    private void stripData(CompoundTag tag) {
        for (String key : dataKeys) tag.remove(key);
    }

    private boolean hasData(CompoundTag tag) {
        return probe.test(tag);
    }

    private boolean verifyData(State expected) {
        State reread = readState(expected.id);
        return reread != null && expected.data.equals(reread.data);
    }

    private static UUID parseId(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    private File stateFile(UUID id) {
        return new File(new File(CustomNpcs.getLevelSaveDirectory(), "data/cnpcplus/" + this.logTag), id.toString() + ".dat");
    }

    private State readState(UUID id) {
        File file = stateFile(id);
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(input);
            State state = new State();
            state.id = id;
            state.owner = UUID.fromString(root.getString("Owner"));
            state.migration = root.getInt("MigrationState");
            state.data = root.getCompound("Data");
            return state;
        } catch (Exception e) {
            LOGGER.error("[{}] store read failed for {}", logTag, id, e);
            return null;
        }
    }

    private void writeState(State state) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putString("Owner", state.owner.toString());
        root.putInt("MigrationState", state.migration);
        root.put("Data", state.data.copy());
        writeCompressed(stateFile(state.id), root);
    }

    private void copyState(State source, UUID id, UUID owner) throws IOException {
        State copy = new State();
        copy.id = id;
        copy.owner = owner;
        copy.data = source.data.copy();
        copy.migration = COPYING;
        writeState(copy);
        if (!verifyData(copy)) throw new IOException("data clone verification failed");
        copy.migration = VERIFIED;
        writeState(copy);
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
