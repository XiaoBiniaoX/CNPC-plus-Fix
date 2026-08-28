package bin.cnpcplus.linked;

import bin.cnpcplus.common.ILinkedDataScriptSyncAccess;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.entity.EntityNPCInterface;

public final class LinkedScriptSync {
    private LinkedScriptSync() {}

    public static boolean isEnabled(LinkedNpcController.LinkedData data) {
        return data instanceof ILinkedDataScriptSyncAccess
                && ((ILinkedDataScriptSyncAccess) data).cnpcplus$getScriptSync();
    }

    public static void load(EntityNPCInterface npc, LinkedNpcController.LinkedData data) {
        if (npc == null || !isEnabled(data) || !data.data.hasKey("Scripts", 9)) {
            return;
        }
        npc.script.readFromNBT(data.data);
        npc.script.lastInited = -1L;
        npc.updateAI = true;
    }

    public static void save(EntityNPCInterface npc, LinkedNpcController.LinkedData data) {
        if (npc == null || !isEnabled(data)) {
            return;
        }
        npc.script.writeToNBT(data.data);
        data.time = System.currentTimeMillis();
    }

    public static NBTTagCompound include(EntityNPCInterface npc, NBTTagCompound compound) {
        if (npc != null && compound != null && isEnabled(npc.linkedData)) {
            npc.script.writeToNBT(compound);
        }
        return compound;
    }
}
