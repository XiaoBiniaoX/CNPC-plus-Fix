package bin.cnpcplus.api;

import bin.cnpcplus.data.NpcExternalData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;

/** Public Java entry point for addon-owned NPC NBT. */
public final class NbtSeparationApi {
    private final NpcExternalData.Store store;

    private NbtSeparationApi(NpcExternalData.Store store) {
        this.store = store;
    }

    /** Register once during addon initialization, before any worlds load. */
    public static NbtSeparationApi register(ResourceLocation id, String... keys) {
        return new NbtSeparationApi(NpcExternalData.registerAddon(id, keys));
    }

    /** Restore external values before your addon deserializes the NPC tag. */
    public boolean prepareLoad(EntityNPCInterface npc, NBTTagCompound tag) {
        requireServerThread(npc);
        return NpcExternalData.prepareLoad(store, npc, tag);
    }

    /** Save the complete current data after writing it to the NPC tag. */
    public void saveNpc(EntityNPCInterface npc, NBTTagCompound tag) {
        requireServerThread(npc);
        NpcExternalData.saveNpc(store, npc, tag);
    }

    /** Restore this store's external values into an exportable tag. */
    public boolean makePortable(NBTTagCompound tag) {
        if (CustomNpcs.Server == null || !CustomNpcs.Server.isCallingFromMinecraftThread()) return false;
        return NpcExternalData.makePortable(store, tag);
    }

    private static void requireServerThread(EntityNPCInterface npc) {
        if (npc != null && npc.world != null && !npc.world.isRemote && CustomNpcs.Server != null
                && !CustomNpcs.Server.isCallingFromMinecraftThread())
            throw new IllegalStateException("NBT separation requires the logical server thread");
    }
}
