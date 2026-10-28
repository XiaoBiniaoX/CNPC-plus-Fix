package bin.cnpcplus.spawner;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.roles.JobSpawner;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.WeakHashMap;

/** 新条目只存服务端克隆引用；旧六槽内联数据无损保留，GUI 仅接收名称摘要。 */
public final class SpawnerData {
    public static final String KEY = "CNPCPlusSpawnerEntries", TITLES = "CNPCPlusSpawnerTitles";
    private static final Map<JobSpawner, NavigableMap<Integer, Entry>> DATA = Collections.synchronizedMap(new WeakHashMap<>());
    public static final class Entry {
        public final int tab;
        public final String name;
        private final NBTTagCompound inline;
        private NBTTagCompound cached;
        public Entry(int tab, String name, NBTTagCompound inline) { this.tab = tab; this.name = name; this.inline = inline; }
        public NBTTagCompound resolve() {
            if (inline != null) return inline.copy();
            if (cached == null) {
                cached = tab >= 1 && tab <= 9 && ServerCloneController.Instance != null
                        ? ServerCloneController.Instance.getCloneData(null, name, tab) : null;
                if (cached == null) cached = new NBTTagCompound();
            }
            return cached.hasKey("id") ? cached.copy() : null;
        }
        NBTTagCompound write(int slot, boolean full) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Slot", slot); tag.setInteger("Tab", tab); tag.setString("Name", name);
            if (full && inline != null) tag.setTag("Inline", inline.copy());
            return tag;
        }
    }
    private SpawnerData() { }
    public static NavigableMap<Integer, Entry> get(JobSpawner job) {
        synchronized (DATA) { return DATA.computeIfAbsent(job, ignored -> new TreeMap<>()); }
    }
    public static NBTTagList write(JobSpawner job, boolean full) {
        NBTTagList tags = new NBTTagList();
        for (Map.Entry<Integer, Entry> entry : get(job).entrySet()) tags.appendTag(entry.getValue().write(entry.getKey(), full));
        return tags;
    }
    public static void read(JobSpawner job, NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 9)) {
            if (!KEY.equals(key)) return;
            boolean legacy = false;
            for (int i = 1; i <= 6; i++) legacy |= tag.hasKey("SpawnerNBT" + i, 10);
            if (!legacy) return;
            get(job).clear();
            for (int i = 1; i <= 6; i++) putInline(job, i, tag.getCompoundTag("SpawnerNBT" + i));
            return;
        }
        NavigableMap<Integer, Entry> map = get(job);
        map.clear();
        NBTTagList list = tag.getTagList(key, 10);
        for (int i = 0; i < list.tagCount() && i < 4096; i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            int slot = item.getInteger("Slot"), tab = item.getInteger("Tab");
            String name = item.getString("Name");
            if (slot < 1 || slot > 1000000 || name.length() > 256) continue;
            map.put(slot, new Entry(tab, name, item.hasKey("Inline", 10) ? item.getCompoundTag("Inline").copy() : null));
        }
    }
    public static void putInline(JobSpawner job, int slot, NBTTagCompound compound) {
        if (slot < 1 || slot > 1000000) return;
        if (compound == null || !compound.hasKey("id")) { get(job).remove(slot); return; }
        String name = compound.hasKey("ClonedName") ? compound.getString("ClonedName") : compound.getString("Name");
        get(job).put(slot, new Entry(0, name.isEmpty() ? compound.getString("id") : name, compound.copy()));
    }
}
