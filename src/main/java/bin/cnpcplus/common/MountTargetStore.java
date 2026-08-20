package bin.cnpcplus.common;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.lang.ref.WeakReference;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/** The mount GUI packet must use the player that opened it, not a global target. */
public final class MountTargetStore {
    private static final Map<UUID, WeakReference<Entity>> TARGETS = new HashMap<UUID, WeakReference<Entity>>();

    private MountTargetStore() {
    }

    public static synchronized void put(EntityPlayer player, Entity target) {
        if (player == null || target == null) return;
        TARGETS.put(player.getUniqueID(), new WeakReference<Entity>(target));
    }

    public static synchronized Entity consume(EntityPlayer player) {
        if (player == null) return null;
        WeakReference<Entity> reference = TARGETS.remove(player.getUniqueID());
        return reference == null ? null : reference.get();
    }
}
