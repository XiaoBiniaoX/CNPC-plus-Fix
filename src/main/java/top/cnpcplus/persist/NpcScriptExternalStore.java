package top.cnpcplus.persist;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;

/** Preserve the existing script lifecycle and linked authority at their Mixin entry points. */
public final class NpcScriptExternalStore {
    public static final String ID_KEY = "CNPCPlusScriptsId";
    public static final String STATE_KEY = "CNPCPlusScriptsMigration";

    private NpcScriptExternalStore() {}

    public static boolean prepareLoad(EntityNPCInterface npc, CompoundTag tag) {
        return NpcDataExternalStore.SCRIPTS.prepareLoad(npc, tag);
    }

    public static void saveNpc(EntityNPCInterface npc, CompoundTag tag) {
        NpcDataExternalStore.SCRIPTS.saveNpc(npc, tag);
    }
}
