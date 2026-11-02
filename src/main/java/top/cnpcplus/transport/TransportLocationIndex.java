package top.cnpcplus.transport;

import java.util.Map;
import noppes.npcs.controllers.data.TransportCategory;
import noppes.npcs.controllers.data.TransportLocation;

/** A stable ID occupies exactly one category and the global index. */
public final class TransportLocationIndex {
    private TransportLocationIndex() {}

    public static void put(Map<Integer, TransportLocation> global,
                           Map<Integer, TransportCategory> categories, TransportLocation location) {
        if (location == null || location.category == null) return;
        remove(global, categories, location.id);
        global.put(location.id, location);
        location.category.locations.put(location.id, location);
    }

    public static TransportLocation remove(Map<Integer, TransportLocation> global,
                                           Map<Integer, TransportCategory> categories, int id) {
        TransportLocation removed = global.remove(id);
        for (TransportCategory category : categories.values()) {
            TransportLocation stale = category.locations.remove(id);
            if (removed == null && stale != null) {
                stale.category = category;
                removed = stale;
            }
        }
        if (removed != null && removed.category != null) removed.category.locations.remove(id);
        return removed;
    }
}
