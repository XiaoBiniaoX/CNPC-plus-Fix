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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NPC 局部 DataScript 三键（Scripts / ScriptLanguage / ScriptEnabled）的外置存储。
 * 严格照 {@code MerchantExternalStore} 模板：状态机 1-5、稳定 UUID、失败保留旧 NBT、ERROR 日志。
 *
 * <p>单文件 blob {@code data/cnpcplus/scripts/<uuid>.dat}，结构
 * {@code {Owner, MigrationState, Data:{三键}}}。原子写（tmp + ATOMIC_MOVE），
 * 因此磁盘上只会存在「完整旧值」或「完整新值」，不存在半截 Data。
 *
 * <p>失败口径（本轮实现要点，勿照抄商人后走样）：
 * <ul>
 *   <li>读档：文件不可读 → 不动文件、不注入，原生 NBT 照常走（键保留在 NBT 里）。</li>
 *   <li>存档：内存脚本为空且磁盘文件不可读/有数据 → 跳过写盘，三键原样保留在 NBT，
 *       绝不用空数据覆盖磁盘（清空脚本的合法删除不受影响——那种场景内存是走读档成功的
 *       ACTIVE 状态，本守卫只挡「没读到过数据」的实例）。</li>
 *   <li>内容与磁盘一致 → 零 IO 快路径（全 NPC 每次世界存档都会走这里）。</li>
 * </ul>
 */
public final class NpcScriptExternalStore {
    public static final String ID_KEY = "CNPCPlusScriptsId";
    public static final String STATE_KEY = "CNPCPlusScriptsMigration";
    private static final String SCRIPTS = "Scripts";
    private static final String LANGUAGE = "ScriptLanguage";
    private static final String ENABLED = "ScriptEnabled";
    private static final String[] SCRIPT_KEYS = { SCRIPTS, LANGUAGE, ENABLED };
    private static final int COPYING = 1;
    private static final int VERIFIED = 2;
    private static final int EXTERNAL_ACTIVE = 3;
    private static final int CLEANUP_PENDING = 4;
    private static final int CLEANED = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger("cnpcplus");
    private static final Map<EntityNPCInterface, State> ACTIVE = new WeakHashMap<>();

    private NpcScriptExternalStore() {}

    private static final class State {
        UUID id;
        UUID owner;
        int migration;
        CompoundTag data = new CompoundTag();
    }

    /**
     * EntityNPCInterface.m_7378_ RETURN 调用。返回 true 表示原生读档时三键缺失、
     * 调用方必须补一次 {@code npc.script.load(tag)}（linked 脚本同步生效时除外）。
     */
    public static synchronized boolean prepareLoad(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || npc.level() == null || npc.level().isClientSide) return false;
        UUID owner = npc.getUUID();
        UUID id = parseId(tag.getString(ID_KEY));
        if (id == null) id = owner;
        State state = readState(id);
        // 内联键即失败回退快照（也包括合法清空），不能被旧外置文件覆盖。
        boolean legacy = tag.contains(SCRIPTS, Tag.TAG_LIST);

        try {
            if (state != null && !owner.equals(state.owner)) {
                UUID replacement = UUID.randomUUID();
                copyState(state, replacement, owner);
                id = replacement;
                state = readState(id);
            }

            if (!legacy && state != null && verifyData(state)) {
                ACTIVE.put(npc, state);
                attachData(tag, state.data);
                tag.putString(ID_KEY, state.id.toString());
                tag.putInt(STATE_KEY, state.migration);
                return true;
            }

            if (!legacy || (state == null && !hasData(tag))) return false;

            State migrated = new State();
            migrated.id = id;
            migrated.owner = owner;
            migrated.data = extractData(tag);
            migrated.migration = COPYING;
            ACTIVE.put(npc, migrated);
            tag.putString(ID_KEY, id.toString());
            tag.putInt(STATE_KEY, COPYING);
            writeState(migrated);
            if (!verifyData(migrated)) throw new IOException("script migration verification failed");
            migrated.migration = VERIFIED;
            writeState(migrated);
            tag.putInt(STATE_KEY, VERIFIED);
            return false;
        } catch (Exception e) {
            ACTIVE.remove(npc);
            LOGGER.error("[scripts] migration failed; script NBT retained for {}", owner, e);
            return false;
        }
    }

    /** EntityNPCInterface.m_7380_ RETURN 调用：外置写盘并从实体 NBT 摘除三键。 */
    public static synchronized void saveNpc(EntityNPCInterface npc, CompoundTag tag) {
        if (npc == null || tag == null || npc.level() == null || npc.level().isClientSide) return;
        UUID owner = npc.getUUID();
        State state = ACTIVE.get(npc);
        UUID id = parseId(tag.getString(ID_KEY));
        if (id == null) id = state != null && owner.equals(state.owner) ? state.id : owner;
        CompoundTag data = extractData(tag);
        File file = stateFile(id);
        if (state != null && (!owner.equals(state.owner) || !id.equals(state.id))) state = null;
        if (state == null) state = readState(id);
        if (state != null && !owner.equals(state.owner)) {
            id = UUID.randomUUID();
            file = stateFile(id);
            state = null;
        }

        try {
            if (!hasData(data)) {
                if (state == null && file.isFile()) {
                    LOGGER.error("[scripts] external store unreadable; script NBT retained for {}", owner);
                    return;
                }
                if (state == null) return;
                if (hasData(state.data) && !ACTIVE.containsKey(npc)) {
                    LOGGER.error("[scripts] in-memory script empty; external store kept for {}", owner);
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

            // 不原地修改 ACTIVE 的已提交快照；失败后的相同内容必须真正重试写盘。
            state = new State();
            state.id = id;
            state.owner = owner;
            state.data = data;
            state.migration = COPYING;
            writeState(state);
            if (!verifyData(state)) throw new IOException("script save verification failed");
            state.migration = EXTERNAL_ACTIVE;
            writeState(state);
            state.migration = CLEANED;
            writeState(state);
            adopt(npc, tag, state);
            stripData(tag);
        } catch (Exception e) {
            LOGGER.error("[scripts] save failed; script NBT retained for {}", owner, e);
        }
    }

    private static void adopt(EntityNPCInterface npc, CompoundTag tag, State state) {
        ACTIVE.put(npc, state);
        tag.putString(ID_KEY, state.id.toString());
        tag.putInt(STATE_KEY, state.migration);
    }

    private static void attachData(CompoundTag tag, CompoundTag data) {
        for (String key : SCRIPT_KEYS) {
            tag.remove(key);
            Tag value = data.get(key);
            if (value != null) tag.put(key, value.copy());
        }
    }

    private static CompoundTag extractData(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        for (String key : SCRIPT_KEYS) {
            Tag value = tag.get(key);
            if (value != null) data.put(key, value.copy());
        }
        return data;
    }

    private static void stripData(CompoundTag tag) {
        for (String key : SCRIPT_KEYS) tag.remove(key);
    }

    private static boolean hasData(CompoundTag tag) {
        return tag.contains(SCRIPTS, Tag.TAG_LIST) && !tag.getList(SCRIPTS, Tag.TAG_COMPOUND).isEmpty();
    }

    private static boolean verifyData(State expected) {
        State reread = readState(expected.id);
        return reread != null && expected.data.equals(reread.data);
    }

    private static UUID parseId(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    private static File stateFile(UUID id) {
        return new File(new File(CustomNpcs.getLevelSaveDirectory(), "data/cnpcplus/scripts"), id.toString() + ".dat");
    }

    private static State readState(UUID id) {
        File file = stateFile(id);
        if (!file.isFile()) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            State state = new State();
            state.id = id;
            state.owner = UUID.fromString(root.getString("Owner"));
            state.migration = root.getInt("MigrationState");
            state.data = root.getCompound("Data");
            return state;
        } catch (Exception e) {
            LOGGER.error("[scripts] store read failed for {}", id, e);
            return null;
        }
    }

    private static void writeState(State state) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putString("Owner", state.owner.toString());
        root.putInt("MigrationState", state.migration);
        root.put("Data", state.data.copy());
        writeCompressed(stateFile(state.id), root);
    }

    private static void copyState(State source, UUID id, UUID owner) throws IOException {
        State copy = new State();
        copy.id = id;
        copy.owner = owner;
        copy.data = source.data.copy();
        copy.migration = COPYING;
        writeState(copy);
        if (!verifyData(copy)) throw new IOException("script clone verification failed");
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
