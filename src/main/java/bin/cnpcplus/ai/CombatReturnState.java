package bin.cnpcplus.ai;

import java.util.Map;
import java.util.WeakHashMap;
import noppes.npcs.entity.EntityNPCInterface;

/** Server-thread-only combat anchor. No save data or strong references to NPCs. */
public final class CombatReturnState {
    private static final Map<EntityNPCInterface, double[]> HOMES = new WeakHashMap<>();
    private CombatReturnState() {}

    private static boolean needsHome(EntityNPCInterface npc) {
        return !npc.isRemote() && !npc.isKilled() && !npc.hasOwner()
                && !npc.isRiding() && !npc.isBeingRidden() && npc.ais.shouldReturnHome();
    }

    public static void begin(EntityNPCInterface npc) {
        if (needsHome(npc)) HOMES.putIfAbsent(npc, new double[]{npc.posX, npc.posY, npc.posZ});
        else HOMES.remove(npc);
    }

    public static double[] destination(EntityNPCInterface npc) {
        double[] home = HOMES.get(npc);
        if (home == null) return null;
        double dx = npc.posX - home[0], dz = npc.posZ - home[2];
        if (!needsHome(npc) || !npc.isAttacking() && dx * dx + dz * dz <= 0.04D) {
            HOMES.remove(npc);
            return null;
        }
        return home;
    }

    public static boolean isReturning(EntityNPCInterface npc) {
        return destination(npc) != null && !npc.isAttacking();
    }
}
