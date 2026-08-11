package bin.cnpcplus.invpage;

import noppes.npcs.entity.data.DataInventory;

import java.util.Map;
import java.util.WeakHashMap;

public class DropPageStore {
    private static final Map<DataInventory, Integer> PAGES = new WeakHashMap<DataInventory, Integer>();

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
