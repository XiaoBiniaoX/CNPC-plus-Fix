package bin.cnpcplus.trader;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.NpcMiscInventory;
import noppes.npcs.roles.RoleTrader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class TraderPager {
    private static final Map<RoleTrader, List<NpcMiscInventory>> CURRENCY = new WeakHashMap<>();
    private static final Map<RoleTrader, List<NpcMiscInventory>> SOLD = new WeakHashMap<>();
    private static final Map<RoleTrader, Integer> PAGE = new WeakHashMap<>();
    private static final Map<RoleTrader, List<String>> TITLES = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private static void ensure(RoleTrader role) {
        synchronized (LOCK) {
            if (CURRENCY.get(role) != null) return;
            List<NpcMiscInventory> c = new ArrayList<>();
            List<NpcMiscInventory> s = new ArrayList<>();
            NpcMiscInventory c0 = new NpcMiscInventory(36);
            NpcMiscInventory s0 = new NpcMiscInventory(18);
            copyTo(role.inventoryCurrency, c0);
            copyTo(role.inventorySold, s0);
            c.add(c0);
            s.add(s0);
            CURRENCY.put(role, c);
            SOLD.put(role, s);
            PAGE.put(role, 0);
            List<String> titles = new ArrayList<>();
            titles.add("");
            TITLES.put(role, titles);
        }
    }

    public static String getPageTitle(RoleTrader role) {
        List<String> titles = TITLES.get(role);
        if (titles == null) return "";
        int p = Math.min(getPage(role), titles.size() - 1);
        if (p < 0) return "";
        return titles.get(p);
    }

    public static void setPageTitle(RoleTrader role, int page, String title) {
        ensure(role);
        synchronized (LOCK) {
            List<String> titles = TITLES.get(role);
            if (page < 0 || page >= titles.size()) return;
            titles.set(page, title == null ? "" : title);
        }
    }

    private static void copyTo(NpcMiscInventory from, NpcMiscInventory to) {
        int n = Math.min(from.getContainerSize(), to.getContainerSize());
        for (int i = 0; i < n; i++) {
            to.setItem(i, from.getItem(i));
        }
    }

    public static int getPageCount(RoleTrader role) {
        List<NpcMiscInventory> list = CURRENCY.get(role);
        return list == null ? 1 : list.size();
    }

    public static int getPage(RoleTrader role) {
        Integer p = PAGE.get(role);
        return p == null ? 0 : p;
    }

    public static void setPageOnly(RoleTrader role, int page) {
        synchronized (LOCK) {
            List<NpcMiscInventory> c = CURRENCY.get(role);
            if (c == null) return;
            if (page < 0) page = 0;
            if (page > c.size() - 1) page = c.size() - 1;
            PAGE.put(role, page);
        }
    }

    public static void switchPage(RoleTrader role, int page) {
        ensure(role);
        synchronized (LOCK) {
            int max = getPageCount(role) - 1;
            if (page < 0) page = 0;
            if (page > max) page = max;
            int cur = getPage(role);
            if (cur == page) {
                return;
            }
            List<NpcMiscInventory> c = CURRENCY.get(role);
            List<NpcMiscInventory> s = SOLD.get(role);
            copyTo(role.inventoryCurrency, c.get(cur));
            copyTo(role.inventorySold, s.get(cur));
            copyTo(c.get(page), role.inventoryCurrency);
            copyTo(s.get(page), role.inventorySold);
            PAGE.put(role, page);
        }
    }

    public static void flushCurrent(RoleTrader role) {
        List<NpcMiscInventory> c = CURRENCY.get(role);
        if (c == null) return;
        int page = getPage(role);
        if (page < 0 || page >= c.size()) return;
        copyTo(role.inventoryCurrency, c.get(page));
        copyTo(role.inventorySold, SOLD.get(role).get(page));
    }

    public static void addPage(RoleTrader role) {
        ensure(role);
        synchronized (LOCK) {
            CURRENCY.get(role).add(new NpcMiscInventory(36));
            SOLD.get(role).add(new NpcMiscInventory(18));
            TITLES.get(role).add("");
        }
    }

    public static boolean removePage(RoleTrader role, int page) {
        ensure(role);
        synchronized (LOCK) {
            if (page <= 0) return false;
            List<NpcMiscInventory> c = CURRENCY.get(role);
            if (page >= c.size()) return false;
            if (getPage(role) == page) {
                switchPage(role, page - 1);
            }
            c.remove(page);
            SOLD.get(role).remove(page);
            TITLES.get(role).remove(page);
            if (getPage(role) > page) {
                PAGE.put(role, getPage(role) - 1);
            }
            return true;
        }
    }

    public static CompoundTag toNBT(RoleTrader role, HolderLookup.Provider lookupProvider) {
        List<NpcMiscInventory> c = CURRENCY.get(role);
        if (c == null) return null;
        List<String> titles = TITLES.get(role);
        boolean anyTitle = false;
        if (titles != null) {
            for (String t : titles) {
                if (!t.isEmpty()) {
                    anyTitle = true;
                    break;
                }
            }
        }
        if (c.size() <= 1 && !anyTitle) return null;
        ListTag pages = new ListTag();
        for (int i = 0; i < c.size(); i++) {
            CompoundTag p = new CompoundTag();
            p.put("Tc", c.get(i).getToNBT(lookupProvider));
            p.put("Ts", SOLD.get(role).get(i).getToNBT(lookupProvider));
            pages.add(p);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("TraderPages", pages);
        tag.putBoolean("FullPages", true);
        if (titles != null) {
            ListTag titleList = new ListTag();
            for (String t : titles) {
                titleList.add(StringTag.valueOf(t));
            }
            tag.put("PageTitles", titleList);
        }
        return tag;
    }

    public static void fromNBT(RoleTrader role, CompoundTag compound, HolderLookup.Provider lookupProvider) {
        synchronized (LOCK) {
            CURRENCY.remove(role);
            SOLD.remove(role);
            PAGE.remove(role);
            TITLES.remove(role);
        }
        ListTag pages = compound.getList("TraderPages", 10);
        boolean full = compound.getBoolean("FullPages");
        List<NpcMiscInventory> c = new ArrayList<>();
        List<NpcMiscInventory> s = new ArrayList<>();
        if (full && !pages.isEmpty()) {
            for (Tag tag : pages) {
                CompoundTag p = (CompoundTag) tag;
                NpcMiscInventory ci = new NpcMiscInventory(36);
                NpcMiscInventory si = new NpcMiscInventory(18);
                ci.setFromNBT(lookupProvider, p.getCompound("Tc"));
                si.setFromNBT(lookupProvider, p.getCompound("Ts"));
                c.add(ci);
                s.add(si);
            }
            copyTo(c.get(0), role.inventoryCurrency);
            copyTo(s.get(0), role.inventorySold);
        } else {
            NpcMiscInventory c0 = new NpcMiscInventory(36);
            NpcMiscInventory s0 = new NpcMiscInventory(18);
            copyTo(role.inventoryCurrency, c0);
            copyTo(role.inventorySold, s0);
            c.add(c0);
            s.add(s0);
            for (Tag tag : pages) {
                CompoundTag p = (CompoundTag) tag;
                NpcMiscInventory ci = new NpcMiscInventory(36);
                NpcMiscInventory si = new NpcMiscInventory(18);
                ci.setFromNBT(lookupProvider, p.getCompound("Tc"));
                si.setFromNBT(lookupProvider, p.getCompound("Ts"));
                c.add(ci);
                s.add(si);
            }
        }
        List<String> titles = new ArrayList<>();
        synchronized (LOCK) {
            CURRENCY.put(role, c);
            SOLD.put(role, s);
            PAGE.put(role, 0);
            ListTag titleList = compound.getList("PageTitles", 8);
            for (Tag tag : titleList) {
                titles.add(tag.getAsString());
            }
            while (titles.size() < c.size()) {
                titles.add("");
            }
            TITLES.put(role, titles);
        }
    }
}
