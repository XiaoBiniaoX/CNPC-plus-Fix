package bin.cnpcplus.invpage;

import noppes.npcs.entity.data.DataInventory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Weak-keyed page store. Wrapped in synchronizedMap: on integrated server the
 * same map is read from the client render thread (slot index shift) and
 * written from the server thread (PacketNpcInvPage) at the same time; a bare
 * WeakHashMap can corrupt its internal chains under concurrent access.
 */
public class DropPageStore {
    private static final Map<DataInventory, Integer> PAGES =
            Collections.synchronizedMap(new WeakHashMap<DataInventory, Integer>());

    public static int get(DataInventory inv) {
        Integer page = PAGES.get(inv);
        return page == null ? 0 : page;
    }

    public static void set(DataInventory inv, int page) {
        if (page < 0) page = 0;
        if (page > 2) page = 2;
        PAGES.put(inv, page);
    }
}
