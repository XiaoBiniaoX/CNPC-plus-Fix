package bin.cnpcplus.trader;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import noppes.npcs.NpcMiscInventory;
import noppes.npcs.roles.RoleTrader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Page cache for RoleTrader. The live fields inventoryCurrency/inventorySold
 * act as the shadow of the current page; switching pages copies page content
 * in and out so every existing slot binding / trade / API keeps working.
 */
public class TraderPager {
    private static final Map<RoleTrader, List<NpcMiscInventory>> CURRENCY = new WeakHashMap<RoleTrader, List<NpcMiscInventory>>();
    private static final Map<RoleTrader, List<NpcMiscInventory>> SOLD = new WeakHashMap<RoleTrader, List<NpcMiscInventory>>();
    private static final Map<RoleTrader, Integer> PAGE = new WeakHashMap<RoleTrader, Integer>();
    private static final Map<RoleTrader, List<String>> TITLES = new WeakHashMap<RoleTrader, List<String>>();
    private static final Object LOCK = new Object();

    private static void ensure(RoleTrader role) {
        synchronized (LOCK) {
            if (CURRENCY.get(role) != null) return;
            List<NpcMiscInventory> c = new ArrayList<NpcMiscInventory>();
            List<NpcMiscInventory> s = new ArrayList<NpcMiscInventory>();
            NpcMiscInventory c0 = new NpcMiscInventory(36);
            NpcMiscInventory s0 = new NpcMiscInventory(18);
            copyTo(role.inventoryCurrency, c0);
            copyTo(role.inventorySold, s0);
            c.add(c0);
            s.add(s0);
            CURRENCY.put(role, c);
            SOLD.put(role, s);
            PAGE.put(role, 0);
            List<String> titles = new ArrayList<String>();
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
        int n = Math.min(from.func_70302_i_(), to.func_70302_i_());
        for (int i = 0; i < n; i++) {
            to.func_70299_a(i, from.func_70301_a(i));
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

    public static void switchPage(RoleTrader role, int page) {
        ensure(role);
        synchronized (LOCK) {
            int max = getPageCount(role) - 1;
            if (page < 0) page = 0;
            if (page > max) page = max;
            int cur = getPage(role);
            if (cur == page) return;
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

    public static NBTTagCompound toNBT(RoleTrader role) {
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
        NBTTagList pages = new NBTTagList();
        for (int i = 0; i < c.size(); i++) {
            NBTTagCompound p = new NBTTagCompound();
            p.setTag("Tc", c.get(i).getToNBT());
            p.setTag("Ts", SOLD.get(role).get(i).getToNBT());
            pages.appendTag(p);
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("TraderPages", pages);
        tag.setBoolean("FullPages", true);
        if (titles != null) {
            NBTTagList titleList = new NBTTagList();
            for (String t : titles) {
                titleList.appendTag(new NBTTagString(t));
            }
            tag.setTag("PageTitles", titleList);
        }
        return tag;
    }

    public static void fromNBT(RoleTrader role, NBTTagCompound compound) {
        synchronized (LOCK) {
            CURRENCY.remove(role);
            SOLD.remove(role);
            PAGE.remove(role);
            TITLES.remove(role);
        }
        NBTTagList pages = compound.getTagList("TraderPages", 10);
        boolean full = compound.getBoolean("FullPages");
        List<NpcMiscInventory> c = new ArrayList<NpcMiscInventory>();
        List<NpcMiscInventory> s = new ArrayList<NpcMiscInventory>();
        if (full && pages.tagCount() > 0) {
            for (int i = 0; i < pages.tagCount(); i++) {
                NBTTagCompound p = pages.getCompoundTagAt(i);
                NpcMiscInventory ci = new NpcMiscInventory(36);
                NpcMiscInventory si = new NpcMiscInventory(18);
                ci.setFromNBT(p.getCompoundTag("Tc"));
                si.setFromNBT(p.getCompoundTag("Ts"));
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
            for (int i = 0; i < pages.tagCount(); i++) {
                NBTTagCompound p = pages.getCompoundTagAt(i);
                NpcMiscInventory ci = new NpcMiscInventory(36);
                NpcMiscInventory si = new NpcMiscInventory(18);
                ci.setFromNBT(p.getCompoundTag("Tc"));
                si.setFromNBT(p.getCompoundTag("Ts"));
                c.add(ci);
                s.add(si);
            }
        }
        List<String> titles = new ArrayList<String>();
        synchronized (LOCK) {
            CURRENCY.put(role, c);
            SOLD.put(role, s);
            PAGE.put(role, 0);
            NBTTagList titleList = compound.getTagList("PageTitles", 8);
            for (int i = 0; i < titleList.tagCount(); i++) {
                NBTBase t = titleList.get(i);
                titles.add(t instanceof NBTTagString ? ((NBTTagString) t).getString() : "");
            }
            while (titles.size() < c.size()) {
                titles.add("");
            }
            TITLES.put(role, titles);
        }
    }
}
