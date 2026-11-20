package top.cnpcplus.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.persist.NpcDataExternalStore;

/** Public Java entry point for addon-owned NPC NBT. No client-side disk access. */
public final class NbtSeparationApi {
    private final NpcDataExternalStore store;

    private NbtSeparationApi(NpcDataExternalStore store) {
        this.store = store;
    }

    /**
     * Register once during addon initialization, before any worlds load.
     * Keys are exact top-level names in the compound passed to the methods below.
     * Use only keys owned by your addon; do not register vanilla or shared containers.
     *
     * @throws IllegalArgumentException for unsafe IDs, empty keys or key conflicts
     * @throws IllegalStateException if the ID is already registered
     */
    public static NbtSeparationApi register(ResourceLocation id, String... keys) {
        return new NbtSeparationApi(NpcDataExternalStore.registerAddon(id, java.util.Set.of(keys)));
    }

    /**
     * Attach external values before your addon deserializes the NPC tag.
     * Inline values take precedence. Safe before the NPC has read its saved UUID.
     * False also covers initial inline migration or a client-side no-op; read the tag normally.
     */
    public boolean prepareLoad(EntityNPCInterface npc, CompoundTag tag) {
        requireServerThread(npc);
        return store.prepareEarlyLoad(npc, tag);
    }

    /**
     * Call after your addon has written its complete current data to the tag.
     * Only removes selected keys after verified storage; IO failure retains inline data.
     * Call on the logical server thread, using the same NPC and compound scope as prepareLoad.
     */
    public void saveNpc(EntityNPCInterface npc, CompoundTag tag) {
        requireServerThread(npc);
        store.saveNpc(npc, tag);
    }

    /**
     * Restore selected data and remove this store's reference for export from the current world.
     * Call on the logical server thread. False retains the reference: abort the export.
     * CNPCplus's NPC clone export already includes registered stores at the NPC tag root.
     */
    public boolean makePortable(CompoundTag tag) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return false;
        return store.makePortable(tag);
    }

    private static void requireServerThread(EntityNPCInterface npc) {
        if (npc == null) return;
        var server = npc.getServer();
        if (server != null && !server.isSameThread())
            throw new IllegalStateException("NBT separation requires the logical server thread");
    }
}
