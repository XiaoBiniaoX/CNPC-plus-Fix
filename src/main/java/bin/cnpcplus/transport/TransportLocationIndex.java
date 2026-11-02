package bin.cnpcplus.transport;

import java.util.Map;
import noppes.npcs.controllers.data.TransportCategory;
import noppes.npcs.controllers.data.TransportLocation;

/** Stable ID shared by global and category indices, including historical category-only points. */
public final class TransportLocationIndex {
    private TransportLocationIndex() { }
    public static void put(Map<Integer, TransportLocation> global, Map<Integer, TransportCategory> categories, TransportLocation location) {
        remove(global, categories, location.id);
        global.put(location.id, location);
        location.category.locations.put(location.id, location);
    }
    public static TransportLocation remove(Map<Integer, TransportLocation> global, Map<Integer, TransportCategory> categories, int id) {
        TransportLocation removed = global.remove(id);
        for (TransportCategory category : categories.values()) {
            TransportLocation stale = category.locations.remove(id);
            if (removed == null && stale != null) { stale.category = category; removed = stale; }
        }
        if (removed != null && removed.category != null) removed.category.locations.remove(id);
        return removed;
    }
}
