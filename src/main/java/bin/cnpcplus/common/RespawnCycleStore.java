package bin.cnpcplus.common;

import java.util.Map;
import java.util.WeakHashMap;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Spawner cycle 3/4 makes the NPC delete itself on death, which prevents any respawn.
 * The cycle is parked while the death runs and restored once the NPC resets.
 */
public final class RespawnCycleStore {
    private static final Map<EntityNPCInterface, Integer> CYCLES = new WeakHashMap<EntityNPCInterface, Integer>();

    private RespawnCycleStore() {
    }

    public static void forceRespawn(EntityNPCInterface npc) {
        if (npc == null || npc.stats == null) return;
        int cycle = npc.stats.spawnCycle;
        if (cycle == 3 || cycle == 4) {
            CYCLES.put(npc, cycle);
            npc.stats.spawnCycle = 0;
        }
    }

    public static void restore(EntityNPCInterface npc) {
        if (npc == null) return;
        Integer cycle = CYCLES.remove(npc);
        if (cycle != null && npc.stats != null) {
            npc.stats.spawnCycle = cycle;
        }
    }
}
