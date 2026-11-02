package bin.cnpcplus.transport;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.TransportController;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.roles.RoleTransporter;

public final class TransportOwnership {
    public static final String KEY = "CNPCPlusTransportOwner";
    private TransportOwnership() { }
    public static UUID owner(TransportLocation location) {
        NBTTagCompound tag = TransportData.location(location);
        return tag.hasUniqueId(KEY) ? tag.getUniqueId(KEY) : null;
    }
    public static void claim(RoleTransporter role) {
        if (role.npc.world.isRemote) return;
        TransportLocation location = role.getLocation();
        if (location == null || location.category == null) return;
        UUID npc = role.npc.getUniqueID();
        UUID owner = owner(location);
        if (owner != null) return;
        NBTTagCompound tag = TransportData.location(location);
        tag.setUniqueId(KEY, npc);
        TransportData.setLocation(location, tag);
        TransportController.getInstance().saveCategories();
    }
}
