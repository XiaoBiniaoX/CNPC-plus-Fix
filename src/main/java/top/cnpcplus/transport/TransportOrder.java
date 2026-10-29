package top.cnpcplus.transport;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;

/** 编辑页和玩家页共用：手动顺序按ID，未排序/新增条目按纯名称A-Z。 */
public final class TransportOrder {
    public static final String KEY = "EntryOrder";

    private TransportOrder() {}

    public static Comparator<String> comparator(CompoundTag panel,
            Function<String, CompoundTag> metadata, Function<String, String> name) {
        Map<Integer, Integer> ranks = new HashMap<>();
        int[] ids = panel.getIntArray(KEY);
        for (int i = 0; i < ids.length; i++) ranks.putIfAbsent(ids[i], i);
        return Comparator.comparingInt((String key) -> {
            CompoundTag meta = metadata.apply(key);
            return meta == null ? Integer.MAX_VALUE : ranks.getOrDefault(meta.getInt("Id"), Integer.MAX_VALUE);
        }).thenComparing(name, String.CASE_INSENSITIVE_ORDER).thenComparing(String::compareTo);
    }
}
