package top.cnpcplus.ai;

import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Tracks NPCs whose return-home goal is currently ticking. */
public final class ReturnHomeState {

    private static final Map<EntityNPCInterface, Boolean> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ReturnHomeState() {
    }

    public static void start(EntityNPCInterface npc) {
        if (npc != null) ACTIVE.put(npc, Boolean.TRUE);
    }

    public static void end(EntityNPCInterface npc) {
        if (npc != null) ACTIVE.remove(npc);
    }

    public static boolean isActive(EntityNPCInterface npc) {
        return npc != null && ACTIVE.containsKey(npc);
    }
}
