package top.cnpcplus.api;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.persist.NpcDataExternalStore;

/** Public Java entry point for addon-owned NPC NBT. Client-side calls do not access files. */
public final class NbtSeparationApi {
    private final NpcDataExternalStore store;

    private NbtSeparationApi(NpcDataExternalStore store) {
        this.store = store;
    }

    /**
     * Register once during addon initialization, before any worlds load.
     * Keys are exact top-level names in the compound passed to the methods below.
     * Use only keys owned by your addon.
     *
     * @throws IllegalArgumentException for unsafe IDs, empty keys or key conflicts
     * @throws IllegalStateException if the ID is already registered
     */
    public static NbtSeparationApi register(ResourceLocation id, String... keys) {
        return new NbtSeparationApi(NpcDataExternalStore.registerAddon(id, Set.of(keys)));
    }

    /**
     * Attach external values before your addon deserializes the NPC tag.
     * Inline values take precedence. Safe before the NPC has read its saved UUID and
     * from NeoForge's entity deserialization worker. Keep the NPC and tag local to that load.
     * False also covers initial inline migration or a client-side no-op. Read the tag normally.
     */
    public boolean prepareLoad(EntityNPCInterface npc, CompoundTag tag) {
        return store.prepareEarlyLoad(npc, tag);
    }

    /**
     * Call on the logical server thread after writing your complete current data to the tag.
     * Only removes selected keys after verified storage. IO failure retains inline data.
     * Use the same NPC and compound scope as in {@link #prepareLoad}.
     */
    public void saveNpc(EntityNPCInterface npc, CompoundTag tag) {
        var server = npc == null ? null : npc.getServer();
        if (server != null && !server.isSameThread())
            throw new IllegalStateException("NBT separation save requires the logical server thread");
        store.saveNpc(npc, tag);
    }

    /**
     * Restore selected data and remove this store's reference for export from the current world.
     * Call on the logical server thread. A false result retains the reference, so abort the export.
     * CNPCplus's NPC clone export already includes registered stores at the NPC tag root.
     */
    public boolean makePortable(CompoundTag tag) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread() && store.makePortable(tag);
    }
}
