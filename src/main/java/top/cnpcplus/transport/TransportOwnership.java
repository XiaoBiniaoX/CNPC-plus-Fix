package top.cnpcplus.transport;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.roles.RoleTransporter;

public final class TransportOwnership {
    public static final String KEY = "CNPCPlusTransportOwner";
    private TransportOwnership() {}

    public static UUID owner(TransportLocation location) {
        return location instanceof TransportVisualMeta meta ? meta.cnpcplus$getOwner() : null;
    }

    public static boolean owns(UUID owner, UUID npc) { return owner != null && owner.equals(npc); }

    public static void claim(RoleTransporter role) {
        if (role.npc == null || role.npc.isClientSide()) return;
        TransportLocation location = role.getLocation();
        if (location == null || location.category == null) return;
        UUID npc = role.npc.getUUID(), owner = owner(location);
        if (owner == null) {
            ((TransportVisualMeta) location).cnpcplus$setOwner(npc);
            TransportController.getInstance().saveCategories();
        } else if (!owns(owner, npc)) {
            CompoundTag snapshot = location.writeNBT().copy();
            snapshot.putInt("Id", -1);
            snapshot.remove(KEY);
            TransportLocation fork = TransportController.getInstance()
                    .saveLocation(location.category.id, snapshot, null, role.npc);
            if (fork != null) role.setTransport(fork);
        }
    }
}
