package bin.cnpcplus.data;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * NPC 的掉落物/脚本/商人/召唤师数据按内容哈希外置到世界存档目录，实体 NBT 里只留引用。
 * 摘除点挂 {@code EntityNPCInterface.writeEntityToNBT} 的出口，和 1.20.1 的
 * {@code m_7380_} 一致：区块存档、克隆、灵魂石、任何读取实体 NBT 的工具都会经过这里。
 * 组件直写的快照（LinkedNpcController.readNpcData、GUI 同步包）不经过实体存档，仍拿内联数据。
 * 写盘当帧同步完成并读回校验，校验通过才摘除；旧档内联数据在读档时自动迁出。
 */
public final class NpcExternalData {
    /** parent 非空表示这组字段在子复合里（商人数据挂在 Role 里）。 */
    private enum Group {
        SCRIPTS("scripts", "CNPCPlusScriptsRef", "", new String[]{"Scripts", "ScriptLanguage", "ScriptEnabled"}),
        SPAWNER("spawner", "CNPCPlusSpawnerRef", "", new String[]{"CNPCPlusSpawnerEntries", "SpawnerNBT1", "SpawnerNBT2", "SpawnerNBT3", "SpawnerNBT4", "SpawnerNBT5", "SpawnerNBT6"}),
        INVENTORY("inventory", "CNPCPlusInventoryRef", "", new String[]{"NpcInv", "DropChance", "Armor", "Weapons", "LootMode", "MinExp", "MaxExp"}),
        // RoleTrader.writeToNBT 直接写在**顶层**实体标签上（javap 证据：
        // EntityNPCInterface.func_70014_b 里 roleInterface.writeToNBT(aload_1)，
        // 只有 role==6 的 Companion 才另开 "Companion" 子复合）。
        // 顶层的 "Role" 是个 int（DataAdvanced.role 角色编号），不是复合标签，
        // 所以以前 parent="Role" 让 host() 恒为 null —— 商人 NBT 从来没被分离过。
        TRADER("trader", "CNPCPlusTraderRef", "", new String[]{"TraderMarket", "TraderCurrency", "TraderSold", "TraderIgnoreDamage", "TraderIgnoreNBT"});
        final String folder, reference, parent;
        final String[] keys;
        Group(String folder, String reference, String parent, String[] keys) {
            this.folder = folder; this.reference = reference; this.parent = parent; this.keys = keys;
        }
        NBTTagCompound host(NBTTagCompound tag) {
            if (parent.isEmpty()) return tag;
            NBTBase base = tag.getTag(parent);
            return base instanceof NBTTagCompound ? (NBTTagCompound) base : null;
        }
    }
    private static final class State {
        NBTTagCompound data;
        String hash, failedReference;
        CompletableFuture<Void> persisted;
    }
    private static final Map<Entity, Map<Group, State>> STATES = new WeakHashMap<>();
    private static final Map<Path, CompletableFuture<Void>> WRITES = new HashMap<>();
    private static final String HASH = "[0-9a-f]{64}";
    private static final Map<Path, NBTTagCompound> READS = new LinkedHashMap<Path, NBTTagCompound>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Path, NBTTagCompound> entry) { return size() > 64; }
    };

    private NpcExternalData() { }

    private static Path path(Group group, String hash) {
        java.io.File root = CustomNpcs.getWorldSaveDirectory();
        if (root == null) throw new IllegalStateException("world save directory unavailable");
        return root.toPath().resolve("data/cnpcplus/" + group.folder).resolve(hash + ".dat");
    }

    private static Map<Group, State> states(Entity npc) { return STATES.computeIfAbsent(npc, ignored -> new EnumMap<>(Group.class)); }

    /**
     * 读档。两种情况：
     * <ul>
     *   <li>已有引用且没有内联 → 从世界目录还原（正常路径）；</li>
     *   <li>旧档内联数据 → 立刻迁出到世界目录并贴回引用，但**保留内联**直到下一次存档。
     *       这样迁移不依赖文件是否写得成功，读档永远不丢数据。</li>
     * </ul>
     */
    public static synchronized void load(EntityNPCInterface npc, NBTTagCompound tag) {
        if (npc.world == null || npc.world.isRemote) return;
        for (Group group : Group.values()) {
            NBTTagCompound host = group.host(tag);
            if (host == null) continue;
            String hash = host.getString(group.reference);
            boolean inline = hasInline(host, group);
            if (inline) { migrateLegacy(npc, host, group, hash); continue; }
            if (!hash.matches(HASH)) continue;
            State state = new State();
            try {
                Path file = path(group, hash);
                if (!Files.isRegularFile(file)) throw new IOException("Missing external NPC data: " + file);
                state.data = READS.get(file);
                if (state.data == null) { state.data = NbtFiles.read(file); READS.put(file, state.data); }
                state.hash = hash;
                state.persisted = CompletableFuture.completedFuture(null);
                WRITES.putIfAbsent(file, state.persisted);
                for (String key : group.keys) if (state.data.hasKey(key)) host.setTag(key, state.data.getTag(key).copy());
            } catch (Exception error) {
                state.failedReference = hash;
                CnpcPlus.LOGGER.error("Cannot restore NPC {} {} data; retaining reference", npc.getUniqueID(), group.folder, error);
            }
            states(npc).put(group, state);
        }
    }

    /** 旧档迁移：内联数据先落到世界目录，引用写回 NBT；内联等下一次保存再摘。 */
    private static void migrateLegacy(Entity npc, NBTTagCompound host, Group group, String reference) {
        try {
            // 旧版本可能只摘了部分键（例如只摘 NpcInv/DropChance，Armor/Weapons 仍内联）。
            // 迁移前先把引用文件里、当前 NBT 缺失的键补回来，否则生成的新快照会丢掉
            // 引用文件中的内容 —— 表现为 NPC 掉落物/装备凭空消失。
            if (reference.matches(HASH)) {
                Path previous = path(group, reference);
                if (Files.isRegularFile(previous)) {
                    NBTTagCompound referenced = READS.get(previous);
                    if (referenced == null) { referenced = NbtFiles.read(previous); READS.put(previous, referenced); }
                    for (String key : group.keys) {
                        if (!host.hasKey(key) && referenced.hasKey(key)) host.setTag(key, referenced.getTag(key).copy());
                    }
                }
            }
            NBTTagCompound payload = extract(host, group);
            if (!nonempty(payload)) return;
            String hash = hash(payload);
            Path file = path(group, hash);
            if (!Files.isRegularFile(file)) {
                NbtFiles.writeNow(file, payload);
                if (!payload.equals(NbtFiles.read(file))) throw new IOException("migrated snapshot verification failed: " + file);
            }
            State state = new State();
            state.data = payload.copy();
            state.hash = hash;
            state.persisted = CompletableFuture.completedFuture(null);
            states(npc).put(group, state);
            WRITES.putIfAbsent(file, state.persisted);
            host.setString(group.reference, hash);
        } catch (Exception error) {
            CnpcPlus.LOGGER.error("Cannot migrate NPC {} {} data; inline NBT retained", npc.getUniqueID(), group.folder, error);
        }
    }

    private static NBTTagCompound extract(NBTTagCompound host, Group group) {
        NBTTagCompound payload = new NBTTagCompound();
        for (String key : group.keys) {
            NBTBase value = host.getTag(key);
            if (value != null) payload.setTag(key, value.copy());
        }
        return payload;
    }

    private static boolean nonempty(NBTTagCompound payload) {
        for (String key : payload.getKeySet()) {
            NBTBase value = payload.getTag(key);
            if (value instanceof NBTTagList) {
                if (((NBTTagList) value).tagCount() > 0) return true;
            } else if (value instanceof NBTTagCompound) {
                if (!((NBTTagCompound) value).isEmpty()) return true;
            } else {
                return true; // 标量（LootMode/MinExp/MaxExp/ScriptLanguage/ScriptEnabled）
            }
        }
        return false;
    }

    private static boolean hasInline(NBTTagCompound tag, Group group) {
        for (String key : group.keys) if (tag.hasKey(key)) return true;
        return false;
    }

    /**
     * 实体存档（EntityNPCInterface.writeEntityToNBT 的出口）摘除内联数据。
     * 挂在实体自身而不是区块存档：区块存档会走这里，克隆/灵魂石/外部 NBT 读取器
     * 也都会走这里，所以任何视角看 NPC 都只剩几字节引用。
     */
    public static synchronized void saveTree(Entity entity, NBTTagCompound tag) {
        if (entity.world == null || entity.world.isRemote) return;
        if (!(entity instanceof EntityNPCInterface)) return;
        for (Group group : Group.values()) save(entity, tag, group);
    }

    private static void save(Entity npc, NBTTagCompound tag, Group group) {
        try {
            NBTTagCompound host = group.host(tag);
            if (host == null) return;
            State state = states(npc).get(group);
            // 没有内联 = 已经被摘除（自己或别的挂钩先跑过）。只补引用，绝不用空数据覆盖文件，
            // 否则重复挂钩会把内容文件洗成空文件。
            if (!hasInline(host, group)) {
                if (state != null && state.hash != null) host.setString(group.reference, state.hash);
                return;
            }
            NBTTagCompound payload = extract(host, group);
            boolean nonempty = nonempty(payload);
            if (state != null && state.failedReference != null && !nonempty) {
                host.setString(group.reference, state.failedReference);
                strip(host, group);
                return;
            }
            if (state == null && !nonempty) return;
            if (state == null || state.data == null || !state.data.equals(payload)) {
                String hash = hash(payload);
                if (state == null || !hash.equals(state.hash)) {
                    Path file = path(group, hash);
                    // 同步写 + 读回校验：同一趟存档里就能确认文件可用，随即摘除内联。
                    NbtFiles.writeNow(file, payload);
                    NBTTagCompound readBack = NbtFiles.read(file);
                    if (!payload.equals(readBack)) throw new IOException("External NPC snapshot verification failed: " + file);
                    READS.put(file, readBack);
                    WRITES.putIfAbsent(file, CompletableFuture.completedFuture(null));
                }
                if (state == null) state = new State();
                state.data = payload.copy();
                state.hash = hash;
                state.failedReference = null;
                state.persisted = CompletableFuture.completedFuture(null);
                states(npc).put(group, state);
            }
            host.setString(group.reference, state.hash);
            strip(host, group);
        } catch (Exception error) {
            CnpcPlus.LOGGER.error("Cannot externalize NPC {} {}; inline NBT retained", npc.getUniqueID(), group.folder, error);
        }
    }

    private static void strip(NBTTagCompound host, Group group) {
        for (String key : group.keys) host.removeTag(key);
    }

    private static String hash(NBTTagCompound tag) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) result.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return result.toString();
    }

    public static synchronized void clear() { NbtFiles.flush(); STATES.clear(); WRITES.clear(); READS.clear(); }
}
