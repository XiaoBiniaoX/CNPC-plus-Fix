package bin.cnpcplus.fx;

import net.minecraft.nbt.NBTTagCompound;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** DataStats、DataRanged 与投掷物共用；value 不持有实体，离开世界即可回收。 */
public final class FxStore {
    public static final String DEATH = "CNPCPlusDeathFx";
    public static final String PROJECTILE = "CNPCPlusProjectileFx";
    private static final Map<Object, List<FxConfig>> DATA = new WeakHashMap<>();

    private FxStore() { }

    public static synchronized List<FxConfig> get(Object owner) {
        List<FxConfig> list = DATA.get(owner);
        return list == null ? Collections.emptyList() : list;
    }

    public static synchronized void put(Object owner, List<FxConfig> list) {
        List<FxConfig> copy = FxConfig.copy(list);
        if (copy.isEmpty()) DATA.remove(owner);
        else DATA.put(owner, Collections.unmodifiableList(copy));
    }

    public static void read(Object owner, NBTTagCompound tag, String key) {
        put(owner, FxConfig.read(tag.getTagList(key, 10)));
    }

    public static void write(Object owner, NBTTagCompound tag, String key) {
        List<FxConfig> list = get(owner);
        if (list.isEmpty()) tag.removeTag(key);
        else tag.setTag(key, FxConfig.write(list));
    }
}
